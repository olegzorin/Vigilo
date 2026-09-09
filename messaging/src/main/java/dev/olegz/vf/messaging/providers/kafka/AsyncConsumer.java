package dev.olegz.vf.messaging.providers.kafka;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

import dev.olegz.vf.messaging.MessageListener;
import dev.olegz.vf.messaging.consumer.AckStatus;
import dev.olegz.vf.messaging.support.MessagingUtils;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asynchronous topic consumer backed by its own thread pool.
 * Records are polled and committed on the consume thread, but dispatched to the pool for processing, so polled
 * records may run concurrently. Each partition is committed independently once its records are marked done (in
 * order).
 * <p>
 * Commit failures are handled asynchronously through {@link #onComplete} (retried for retriable errors); there is
 * deliberately no blocking retry on the consume thread, which would risk a poll timeout and rebalance.
 */
class AsyncConsumer extends TopicConsumer {
    private static final Logger logger = LoggerFactory.getLogger(AsyncConsumer.class);

    private static final Duration timeout = Duration.ofMillis(500);

    // Always throws RejectedExecutionException, if the queue is full
    private static final ThreadPoolExecutor.AbortPolicy rejectedExecutionHandler = new ThreadPoolExecutor.AbortPolicy();

    private final long maxCommitInterval;
    private final ThreadPoolExecutor pool;

    AsyncConsumer(String topic, Map<String, Object> props, MessageListener listener, String clientId, int threadPoolSize, long maxCommitInterval) {
        super(topic, props, listener, clientId);
        this.maxCommitInterval = maxCommitInterval;
        this.pool = makePool(threadPoolSize, clientId);
    }

    AsyncConsumer(String topic, Consumer<byte[], byte[]> consumer, MessageListener listener, String clientId,
        int threadPoolSize, long maxCommitInterval)
    {
        super(topic, consumer, listener, clientId);
        this.maxCommitInterval = maxCommitInterval;
        this.pool = makePool(threadPoolSize, clientId);
    }

    private static ThreadPoolExecutor makePool(int threadPoolSize, String clientId) {
        ThreadFactory threadFactory = MessagingUtils.threadFactory("ConsumerWorker-", clientId, logger);
        if (threadPoolSize > 0) {
            ThreadPoolExecutor pool = new ThreadPoolExecutor(threadPoolSize, threadPoolSize, 0L,
                TimeUnit.MILLISECONDS, new SynchronousQueue<>(), threadFactory);
            pool.setRejectedExecutionHandler(rejectedExecutionHandler);
            return pool;
        }
        return new ThreadPoolExecutor(1, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(), threadFactory);
    }

    @Override
    void shutdownNow() {
        pool.shutdownNow();
    }

    /**
     * A processed (or in-flight) record paired with its Kafka offset, kept in offset order per partition.
     */
    private record Committable(long offset, AckStatus ack) {
    }

    // poll variables
    private ConsumerRecord<byte[], byte[]> currentRecord;
    private final ArrayDeque<ConsumerRecord<byte[], byte[]>> bufferedRecords = new ArrayDeque<>();

    @Override
    void poll() {
        try {
            if (currentRecord != null) {
                if (!listener.expired(currentRecord.timestamp())) {
                    processRecord(currentRecord);
                }
                currentRecord = null;
            }

            if (bufferedRecords.isEmpty()) {
                buffer(consumer.poll(timeout));
                if (bufferedRecords.isEmpty()) return;

                commit(); // commit records processed during polling timeout
            }

            while ((currentRecord = bufferedRecords.pollFirst()) != null) {
                if (listener.expired(currentRecord.timestamp())) {
                    currentRecord = null;
                    continue;
                }

                processRecord(currentRecord);
                currentRecord = null;

                if (lastCommitTime + maxCommitInterval < System.currentTimeMillis()) {
                    commit();
                }
            }
        } catch (RejectedExecutionException ignore) {
            pollWhileBackpressured();
        } finally {
            commit();
        }
    }

    private void pollWhileBackpressured() {
        Set<TopicPartition> assignedPartitions = consumer.assignment();
        if (assignedPartitions.isEmpty()) return;

        consumer.pause(assignedPartitions);
        try {
            // Paused partitions do not return records, but poll() must continue for heartbeats and rebalances.
            // Buffer any records from partitions assigned by a rebalance during this poll instead of dropping them.
            buffer(consumer.poll(timeout));
        } finally {
            Set<TopicPartition> currentAssignment = consumer.assignment();
            if (!currentAssignment.isEmpty()) consumer.resume(currentAssignment);
        }
    }

    private void buffer(ConsumerRecords<byte[], byte[]> records) {
        for (ConsumerRecord<byte[], byte[]> record : records) bufferedRecords.addLast(record);
    }

    // process variables
    private final HashMap<Integer, LinkedList<Committable>> processedRecords = new HashMap<>(4);
    private final HashMap<Integer, Long> processedOffsets = new HashMap<>(4);

    private void processRecord(ConsumerRecord<byte[], byte[]> record) throws RejectedExecutionException
    {
        final Integer partition = record.partition();
        final long offset = record.offset();

        Long processedOffset = processedOffsets.get(partition);
        if ((processedOffset != null) && (processedOffset >= offset)) {
            if (logger.isDebugEnabled()) {
                logger.debug("{} ignore duplicate record: partition={}, offset={}", clientId, partition, offset);
            }
            return;
        }

        AckStatus ack = new AckStatus();
        pool.execute(() -> listener.onMessage(record.value(), ack));

        // update variables only if the executor accepted a new record
        processedRecords.computeIfAbsent(partition, _ -> new LinkedList<>()).add(new Committable(offset, ack));
        processedOffsets.put(partition, offset);
    }

    // commit variables
    private long lastCommitTime;
    private final BiConsumer<Integer, LinkedList<Committable>> partitionCommitter = this::commitPartition;

    private void commit() {
        // collect the highest contiguous done offset per partition
        processedRecords.forEach(partitionCommitter);

        flushCommit();
        lastCommitTime = System.currentTimeMillis();
    }

    private void commitPartition(int partition, LinkedList<Committable> records) {
        Committable c;
        long offset = 0;
        boolean commit = false;

        while (((c = records.peek()) != null) && c.ack().isAcknowledged()) {
            offset = c.offset();
            commit = true;
            records.remove();
        }

        if (commit) {
            stageCommit(partition, offset);
            if (logger.isDebugEnabled()) {
                logger.debug("{} commit partition={}, offset={}", clientId, partition, offset);
            }
        }
    }
}
