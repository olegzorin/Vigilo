package dev.olegz.vf.messaging.providers.kafka;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import dev.olegz.vf.messaging.MessageListener;
import dev.olegz.vf.messaging.RetryableMessageException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Synchronous topic consumer.
 * Data records are polled, processed and committed in the same thread.
 */
class SyncConsumer extends TopicConsumer {
    private static final Logger logger = LoggerFactory.getLogger(SyncConsumer.class);

    private static final Duration timeout = Duration.ofMillis(1000L);
    private static final long retryBackoffMillis = 1000L;
    private final long maxCommitInterval;
    // last processed offset per partition (poll thread only)
    protected final HashMap<Integer, Long> offsets = new HashMap<>(2);

    SyncConsumer(String topic, Map<String, Object> props, MessageListener listener, String clientId, long maxCommitInterval) {
        super(topic, props, listener, clientId);
        this.maxCommitInterval = maxCommitInterval;
    }

    SyncConsumer(String topic, Consumer<byte[], byte[]> consumer, MessageListener listener, String clientId,
        long maxCommitInterval)
    {
        super(topic, consumer, listener, clientId);
        this.maxCommitInterval = maxCommitInterval;
    }

    @Override
    void poll() {
        try {
            ConsumerRecords<byte[], byte[]> records = consumer.poll(timeout);
            if (records.count() == 0) return;

            if (processRecords(records)) backOffBeforeRetry();
        } finally {
            commit();
        }
    }

    protected boolean processRecords(ConsumerRecords<byte[], byte[]> records) {
        long lastCommitTime = System.currentTimeMillis();
        boolean retry = false;

        for (TopicPartition partition : records.partitions()) {
            for (ConsumerRecord<byte[], byte[]> record : records.records(partition)) {
                if (listener.expired(record.timestamp())) {
                    offsets.put(record.partition(), record.offset());
                    continue;
                }

                try {
                    listener.onMessage(record.value());
                } catch (RetryableMessageException e) {
                    consumer.seek(partition, record.offset());
                    retry = true;
                    logger.warn("{} will retry record on topic={}, partition={}, offset={}: {}",
                        clientId, topic, record.partition(), record.offset(), e.toString());
                    break;
                } catch (Exception e) {
                    // Unclassified failures retain the historical skip behavior. Durable listeners must use
                    // RetryableMessageException for failures that cannot be acknowledged safely.
                    logger.error("{} failed to process record on topic={}, partition={}, offset={}; skipping",
                        clientId, topic, record.partition(), record.offset(), e);
                }

                final long endTime = System.currentTimeMillis();

                offsets.put(record.partition(), record.offset());

                if (lastCommitTime + maxCommitInterval < endTime) {
                    commit();
                    lastCommitTime = endTime;
                }
            }
        }
        return retry;
    }

    private static void backOffBeforeRetry() {
        try {
            Thread.sleep(retryBackoffMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void commit() {
        if (!listener.commit(System.currentTimeMillis()) || offsets.isEmpty()) return;

        if (logger.isDebugEnabled()) logger.debug("{} commit {}", clientId, offsets);

        offsets.forEach(this::stageCommit);
        flushCommit();
        offsets.clear();
    }
}
