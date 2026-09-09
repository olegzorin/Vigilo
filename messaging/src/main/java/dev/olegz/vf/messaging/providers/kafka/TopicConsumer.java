package dev.olegz.vf.messaging.providers.kafka;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import dev.olegz.vf.messaging.MessageListener;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Kafka consumer bound to a single topic and registered with {@link KafkaMessageBroker}.
 * Carries the {@code topic} and Kafka {@code clientId}, owns the consume loop (start/run/stop) on its own thread,
 * and the asynchronous commit + retry handling with the offset commit buffer shared by its subclasses. A subclass
 * implements {@link #poll()}; the broker tracks and shuts them all down through this type.
 */
abstract class TopicConsumer implements Runnable, OffsetCommitCallback {
    private static final Logger logger = LoggerFactory.getLogger(TopicConsumer.class);

    // On shutdown the consumer does not send a leave-group request, so a rolling
    // restart doesn't trigger an immediate rebalance — the broker reassigns partitions
    // only after the session timeout (session.timeout.ms) elapses, by which point
    // the restarted instance has usually rejoined.
    // Note: to actually avoid rebalance churn, the restart needs to complete within
    // session.timeout.ms. If an instance stays down longer than that, the rebalance
    // happens anyway — which is the correct fallback.
    private static final CloseOptions CONSUMER_CLOSE_OPTIONS =
        CloseOptions.timeout(Duration.ofSeconds(10))
            .withGroupMembershipOperation(
                CloseOptions.GroupMembershipOperation.REMAIN_IN_GROUP);

    final String topic;
    final String clientId;

    final Consumer<byte[], byte[]> consumer;
    final MessageListener listener;
    private volatile boolean running;
    private final AtomicInteger commitFails = new AtomicInteger();

    // offset commit buffer (poll thread only)
    private final HashMap<Integer, TopicPartition> partitionCache = new HashMap<>(4);
    private final HashMap<TopicPartition, OffsetAndMetadata> commitBuffer = new HashMap<>(4);

    TopicConsumer(String topic, Map<String, Object> props, MessageListener listener, String clientId) {
        this(topic, new KafkaConsumer<>(props), listener, clientId);
    }

    TopicConsumer(String topic, Consumer<byte[], byte[]> consumer, MessageListener listener, String clientId) {
        this.topic = topic;
        this.clientId = clientId;
        this.consumer = consumer;
        this.listener = listener;
    }

    /**
     * Begin consuming the topic.
     */
    void start() {
        if (running) {
            logger.warn("Trying to start already running consumer {}", clientId);
            return;
        }

        running = true;
        Thread thread = new Thread(this, "Consumer-" + clientId);
        thread.setUncaughtExceptionHandler((t, e) -> logger.error("Uncaught exception in {}", t.getName(), e));
        thread.start();
    }

    @Override
    public void run() {
        try {
            consumer.subscribe(List.of(topic));

            // Infinite loop to poll and commit data records
            while (running) {
                try {
                    poll();
                } catch (Exception e) {
                    if (running) {
                        logger.error("Exception in polling {} from {}", clientId, topic, e);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Exception in running {} on {}", clientId, topic, e);
        } finally {
            try {
                consumer.close(CONSUMER_CLOSE_OPTIONS);
                shutdownNow();
            } catch (Exception e) {
                logger.warn("Exception in closing {} on {}", clientId, topic, e);
            }
        }
    }

    /**
     * Stop consuming and release resources. May be called from another thread.
     */
    void close() {
        running = false;
        consumer.wakeup();
    }

    @Override
    public void onComplete(Map<TopicPartition, OffsetAndMetadata> commitOffsets, Exception e) {
        if (e == null) {
            commitFails.set(0);
        } else {
            logger.warn("Exception in commit of {}, topic={}, offset={}", clientId, topic, commitOffsets, e);

            if ((e instanceof RetriableCommitFailedException) &&
                (commitOffsets != null) && !commitOffsets.isEmpty() && (commitFails.getAndIncrement() < 2))
            {
                consumer.commitAsync(commitOffsets, this);
            }
        }
    }

    /**
     * Stage the next offset to commit for a partition. The committed offset is always the offset of the next
     * message to read, hence {@code lastProcessedOffset + 1}.
     */
    final void stageCommit(int partition, long lastProcessedOffset) {
        commitBuffer.put(
            partitionCache.computeIfAbsent(partition, p -> new TopicPartition(topic, p)),
            new OffsetAndMetadata(lastProcessedOffset + 1));
    }

    /**
     * Asynchronously commit everything staged so far. Kafka copies the offsets, so the buffer is cleared at once.
     */
    final void flushCommit() {
        if (commitBuffer.isEmpty()) return;
        consumer.commitAsync(commitBuffer, this);
        commitBuffer.clear();
    }

    void shutdownNow() {
    }

    abstract void poll();

    @Override
    public String toString() {
        return getClass().getSimpleName() + '[' + clientId + ", topic=" + topic + ']';
    }
}
