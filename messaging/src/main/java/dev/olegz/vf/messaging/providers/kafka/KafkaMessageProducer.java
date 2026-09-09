package dev.olegz.vf.messaging.providers.kafka;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import dev.olegz.vf.messaging.ConfirmingKeyedMessageProducer;
import dev.olegz.vf.messaging.MessageProducer;
import dev.olegz.vf.messaging.MessagingException;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka {@link MessageProducer}. A record consists of a topic, an optional key and a value:
 * a key routes related records to the same partition; without a key partitions are chosen round-robin.
 */
class KafkaMessageProducer implements ConfirmingKeyedMessageProducer, Callback {
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageProducer.class);

    private final String clientId;
    private final KafkaProducer<byte[], byte[]> producer;
    private final int maxMessageSize;
    private final int warnMessageSize;

    KafkaMessageProducer(String clientId, Map<String, Object> props, int maxMessageSize, int warnMessageSize) {
        this.clientId = clientId;
        this.producer = new KafkaProducer<>(props);
        this.maxMessageSize = maxMessageSize;
        this.warnMessageSize = warnMessageSize;
    }

    @Override
    public void send(String topic, byte[] value) {
        checkSize(topic, null, 0, value);
        send(new ProducerRecord<>(topic, value));
    }

    @Override
    public void send(String topic, String key, byte[] value) {
        checkSize(topic, key, 0, value);
        send(new ProducerRecord<>(topic, key.getBytes(StandardCharsets.UTF_8), value));
    }

    @Override
    public void send(String topic, int key, byte[] value) {
        checkSize(topic, null, key, value);
        send(new ProducerRecord<>(topic, intToBytes(key), value));
    }

    private void send(ProducerRecord<byte[], byte[]> record) {
        try {
            producer.send(record, this);
        } catch (IllegalStateException e) {
            logger.warn("Producer closed for topic={}, clientId={}", record.topic(), clientId, e);
        }
    }

    @Override
    public void sendAndAwait(String topic, byte[] value) {
        checkSize(topic, null, 0, value);
        sendAndAwait(new ProducerRecord<>(topic, value));
    }

    @Override
    public void sendAndAwait(String topic, String key, byte[] value) {
        checkSize(topic, key, 0, value);
        sendAndAwait(new ProducerRecord<>(topic, key.getBytes(StandardCharsets.UTF_8), value));
    }

    @Override
    public void sendAndAwait(String topic, int key, byte[] value) {
        checkSize(topic, null, key, value);
        sendAndAwait(new ProducerRecord<>(topic, intToBytes(key), value));
    }

    private void sendAndAwait(ProducerRecord<byte[], byte[]> record) {
        try {
            producer.send(record).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessagingException("Interrupted waiting for Kafka acknowledgement: topic=" + record.topic() +
                ", clientId=" + clientId, e);
        } catch (ExecutionException | IllegalStateException e) {
            throw new MessagingException("Kafka send was not acknowledged: topic=" + record.topic() +
                ", clientId=" + clientId, e);
        }
    }

    public void close() {
        producer.close();
    }

    @Override
    public void onCompletion(RecordMetadata recordMetadata, Exception e) {
        if (e != null) {
            logger.error("Exception in sending by clientId={} to {}", clientId, recordMetadata, e);
        }
    }

    @Override
    public String toString() {
        return clientId;
    }

    private void checkSize(String topic, String key, int intKey, byte[] value) {
        if (value.length > maxMessageSize) {
            // TODO: for payloads above the hard cap, store the value in S3 and publish a reference (claim-check pattern)
            //       instead of failing the send. Until then, reject it so the oversized record never reaches the broker.
            throw new MessagingException("Message exceeds max size: size=" + value.length + ", maxSize=" + maxMessageSize
                + ", topic=" + topic + ", clientId=" + clientId + keyInfo(key, intKey));
        }
        if (value.length > warnMessageSize) {
            logger.warn("Large message: size={}, warnSize={}, topic={}, clientId={}{}",
                value.length, warnMessageSize, topic, clientId, keyInfo(key, intKey));
        }
    }

    private static String keyInfo(String key, int intKey) {
        return key != null ? ", key=" + key : intKey != 0 ? ", key=" + intKey : "";
    }

    /**
     * Convert integer to byte array putting lowers bytes to the front for better randomization of the result
     */
    private static byte[] intToBytes(int i) {
        return new byte[] {
            (byte) (i & 0xFF),
            (byte) ((i >> 8) & 0xFF),
            (byte) ((i >> 16) & 0xFF),
            (byte) ((i >> 24) & 0xFF)
        };
    }
}
