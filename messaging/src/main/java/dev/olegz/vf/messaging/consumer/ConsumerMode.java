package dev.olegz.vf.messaging.consumer;

import dev.olegz.vf.messaging.MessageBroker;
import dev.olegz.vf.messaging.MessageListener;

/**
 * Whether a topic's records must be processed in order or may be processed concurrently for throughput. This
 * states the caller's intent; each broker realizes it with whatever mechanism fits.
 *
 * @see MessageBroker#setMessageListeners(String, MessageListener, ConsumerConfig)
 */
public enum ConsumerMode {
    /**
     * Preserve the order of related records (those sharing a routing key). Throughput is bounded by how far the
     * broker can parallelize without breaking that order.
     * <p>
     * Kafka realization: {@code poolSize} independent single-thread consumers, each owning a subset of the topic's
     * partitions, so records within a partition are handled sequentially.
     * <p>
     * Artemis realization: {@code poolSize} competing consumers on one queue, with message grouping (the producer's
     * key) pinning each key to a single consumer, so per-key order is preserved across the pool.
     */
    ORDERED,

    /**
     * Maximize throughput with no ordering guarantee: records are spread across worker threads and may be processed
     * in any order relative to one another.
     * <p>
     * Kafka realization: one consumer feeding a {@code poolSize}-thread pool.
     * <p>
     * Artemis realization: one consumer feeding a {@code poolSize}-thread pool; because all records pass through the
     * single consumer and fan out to the pool, producer grouping is ignored and order is not preserved.
     */
    CONCURRENT
}
