package dev.olegz.vf.messaging.support;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import dev.olegz.vf.messaging.*;
import dev.olegz.vf.messaging.consumer.ConsumerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-process {@link MessageBroker} for {@link MessagingProvider#LOOPBACK}. It loops produced records straight back
 * to the listeners registered for the same topic, without any external broker. Used when messaging is disabled:
 * unlike a silent no-op it lets a single JVM exercise the full produce&rarr;consume path, which is convenient in tests.
 * <p>
 * Delivery is synchronous on the producing thread and fans out to every listener registered for the topic. The record
 * key and all consumer-group / pool / commit settings are ignored &mdash; they have no meaning without partitions.
 * Listener exceptions are logged and swallowed so one failing listener cannot block the producer, mirroring the Kafka
 * consumer's per-record error handling.
 */
public final class LoopbackMessageBroker implements MessageBroker {
    public static final LoopbackMessageBroker INSTANCE = new LoopbackMessageBroker();

    private static final Logger logger = LoggerFactory.getLogger(LoopbackMessageBroker.class);

    private final Map<String, List<MessageListener>> listeners = new ConcurrentHashMap<>();
    private final MessageProducer producer = new LoopbackProducer();

    private LoopbackMessageBroker() {
    }

    @Override
    public MessagingProvider provider() {
        return MessagingProvider.LOOPBACK;
    }

    @Override
    public MessageProducer getProducer(String name) {
        return producer;
    }

    @Override
    public void setMessageListener(String topic, MessageListener listener, int maxPollInterval, int maxPollRecords) {
        register(topic, listener);
    }

    @Override
    public void setMessageListeners(String topic, MessageListener listener, ConsumerConfig config) {
        register(topic, listener);
    }

    private void register(String topic, MessageListener listener) {
        listeners.computeIfAbsent(topic, _ -> new CopyOnWriteArrayList<>()).add(listener);
    }

    private void deliver(String topic, byte[] value) {
        List<MessageListener> topicListeners = listeners.get(topic);
        if (topicListeners == null) return;

        long now = System.currentTimeMillis();
        for (MessageListener listener : topicListeners) {
            if (listener.expired(now)) continue;
            try {
                listener.onMessage(value);
            } catch (Exception e) {
                logger.error("In-memory listener failed to process a record on topic={}; skipping", topic, e);
            }
        }
    }

    @Override
    public int maxMessageSize() {
        return DEFAULT_MAX_MESSAGE_SIZE;
    }

    @Override
    public void closeProducers() {
    }

    @Override
    public void close() {
        listeners.clear();
    }

    /** Producer that delivers every sent value back through the broker; the routing key has no effect in-memory. */
    private final class LoopbackProducer implements ConfirmingKeyedMessageProducer {
        @Override public void send(String topic, byte[] value) { deliver(topic, value); }
        @Override public void sendAndAwait(String topic, byte[] value) { deliver(topic, value); }
        @Override public void sendAndAwait(String topic, String key, byte[] value) { deliver(topic, value); }
        @Override public void sendAndAwait(String topic, int key, byte[] value) { deliver(topic, value); }
        @Override public void send(String topic, String key, byte[] value) { deliver(topic, value); }
        @Override public void send(String topic, int key, byte[] value) { deliver(topic, value); }
    }
}
