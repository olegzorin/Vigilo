package dev.olegz.vf.messaging.providers.artemis;

import dev.olegz.vf.messaging.KeyedMessageProducer;
import dev.olegz.vf.messaging.MessageProducer;
import dev.olegz.vf.messaging.MessagingException;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.SendAcknowledgementHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Artemis {@link MessageProducer}. A record is a topic (Artemis address), an optional key and a value: the key
 * becomes the message <em>group id</em>, which routes related records to the same consumer so their order is
 * preserved; without a key records are spread round-robin.
 * <p>
 * The Artemis core {@link ClientSession}/{@link ClientProducer} are not thread-safe, so {@link #send} is
 * synchronized. Sends are non-blocking (the locator disables block-on-send), so the critical section only buffers
 * the record; delivery failures are reported asynchronously through {@link SendAcknowledgementHandler}, mirroring
 * the Kafka producer's completion callback.
 */
class ArtemisMessageProducer implements KeyedMessageProducer, SendAcknowledgementHandler {
    private static final Logger logger = LoggerFactory.getLogger(ArtemisMessageProducer.class);

    private final ArtemisMessageBroker broker;
    private final String clientId;
    private final ClientSession session;
    private final ClientProducer producer;
    private final int maxMessageSize;
    private final int warnMessageSize;

    ArtemisMessageProducer(ArtemisMessageBroker broker, String clientId, ClientSession session,
        int maxMessageSize, int warnMessageSize)
    {
        this.broker = broker;
        this.clientId = clientId;
        this.session = session;
        this.maxMessageSize = maxMessageSize;
        this.warnMessageSize = warnMessageSize;
        try {
            // Anonymous producer: the destination address is supplied per send().
            this.producer = session.createProducer();
        } catch (ActiveMQException e) {
            throw new MessagingException("Cannot create Artemis producer " + clientId, e);
        }
    }

    @Override
    public void send(String topic, byte[] value) {
        checkSize(topic, null, value);
        send(topic, null, null, value);
    }

    @Override
    public void send(String topic, String key, byte[] value) {
        checkSize(topic, key, value);
        send(topic, key, key, value);
    }

    private synchronized void send(String topic, String key, String group, byte[] value) {
        try {
            RoutingType rt = broker.routingFor(topic);
            // Durable for point-to-point work queues (parity with Kafka's persistent log); broadcast (multicast)
            // records are live-only, matching per-server queues that need no persistence.
            ClientMessage message = session.createMessage(rt == RoutingType.ANYCAST);
            message.setRoutingType(rt);
            message.setTimestamp(System.currentTimeMillis());
            if (group != null) {
                message.putStringProperty(Message.HDR_GROUP_ID, SimpleString.of(group));
            }
            message.getBodyBuffer().writeBytes(value);

            producer.send(SimpleString.of(topic), message, this);
        } catch (ActiveMQException e) {
            logger.error("Exception in sending by clientId={} to topic={}{}", clientId, topic, keyInfo(key), e);
        }
    }

    @Override
    public void sendAcknowledged(Message message) {
        // Broker confirmed receipt; nothing to do.
    }

    @Override
    public void sendFailed(Message message, Exception e) {
        logger.error("Send failed by clientId={} to address={}", clientId, message.getAddress(), e);
    }

    void close() {
        try {
            producer.close();
        } catch (Exception e) {
            logger.warn("Exception closing producer {}", clientId, e);
        }
        try {
            session.close();
        } catch (Exception e) {
            logger.warn("Exception closing producer session {}", clientId, e);
        }
    }

    @Override
    public String toString() {
        return clientId;
    }

    private void checkSize(String topic, String key, byte[] value) {
        if (value.length > maxMessageSize) {
            // TODO: for payloads above the hard cap, store the value in S3 and publish a reference (claim-check
            //       pattern) instead of failing the send. Until then, reject it so the oversized record never
            //       reaches the broker. (Artemis streams large messages, so this cap is for parity, not a hard limit.)
            throw new MessagingException("Message exceeds max size: size=" + value.length + ", maxSize=" + maxMessageSize
                + ", topic=" + topic + ", clientId=" + clientId + keyInfo(key));
        }
        if (value.length > warnMessageSize) {
            logger.warn("Large message: size={}, warnSize={}, topic={}, clientId={}{}",
                value.length, warnMessageSize, topic, clientId, keyInfo(key));
        }
    }

    private static String keyInfo(String key) {
        return key != null ? ", key=" + key : "";
    }
}
