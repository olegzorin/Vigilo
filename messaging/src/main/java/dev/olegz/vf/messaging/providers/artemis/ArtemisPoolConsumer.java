package dev.olegz.vf.messaging.providers.artemis;

import java.util.ArrayDeque;
import java.util.concurrent.*;

import dev.olegz.vf.messaging.MessageListener;
import dev.olegz.vf.messaging.consumer.AckStatus;
import dev.olegz.vf.messaging.consumer.ConsumerMode;
import dev.olegz.vf.messaging.support.MessagingUtils;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Concurrent Artemis consumer realizing {@link ConsumerMode#CONCURRENT}: a single consumer whose delivery thread
 * fans records out to a {@code poolSize}-thread pool, so records are processed in parallel and ordering is not
 * preserved. This is the direct counterpart of the Kafka {@code AsyncConsumer} (one consumer feeding a thread pool)
 * and the reason the mode is meaningful on Artemis: because every record arrives at this one consumer and is handed
 * to the pool, any producer-stamped message group id is effectively ignored, so {@code CONCURRENT} behaves the same
 * whether or not callers send keys &mdash; matching Kafka, where the thread pool likewise breaks per-key order.
 * ({@link ConsumerMode#ORDERED} instead uses {@code poolSize} competing {@link ArtemisConsumer}s, where grouping
 * pins each key to one consumer and order is kept.)
 * <p>
 * Artemis core acknowledgement is cumulative per consumer, so acks are staged in delivery order: a record is handed
 * to the pool with its own {@link AckStatus}, and on the delivery thread the longest run of completed records at the
 * head is acknowledged (one cumulative {@code acknowledge()} on the last of them) and committed on the
 * {@code maxCommitInterval} cadence. A record is never acknowledged while an earlier one is still in flight, giving
 * the same at-least-once guarantee as the sequential consumer. The semaphore bounds in-flight processing to
 * {@code poolSize} and, by blocking the delivery thread when the pool is saturated, back-pressures Artemis delivery.
 */
class ArtemisPoolConsumer extends ArtemisConsumer {
    private static final Logger logger = LoggerFactory.getLogger(ArtemisPoolConsumer.class);

    private static final long SHUTDOWN_DRAIN_SECONDS = 30L;

    private final ThreadPoolExecutor pool;
    private final Semaphore permits;
    // Records handed out (or skipped as expired) in delivery order, awaiting cumulative ack. Delivery thread only,
    // plus the shutdown thread in beforeSessionClose() after delivery has stopped.
    private final ArrayDeque<Committable> inflight = new ArrayDeque<>();

    private record Committable(ClientMessage message, AckStatus ack) {
    }

    ArtemisPoolConsumer(ArtemisMessageBroker broker, String topic, String queue, RoutingType routingType,
        MessageListener listener, String clientId, long maxCommitInterval, int poolSize)
    {
        super(broker, topic, queue, routingType, listener, clientId, maxCommitInterval);
        this.permits = new Semaphore(poolSize);
        this.pool = new ThreadPoolExecutor(poolSize, poolSize, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(), MessagingUtils.threadFactory("ArtemisWorker-", clientId, logger));
    }

    @Override
    public void onMessage(ClientMessage message) {
        if (!running) return;

        AckStatus ack = new AckStatus();
        if (listener.expired(message.getTimestamp())) {
            // Skip, but keep its slot so the cumulative ack stays in delivery order.
            ack.acknowledge();
            inflight.add(new Committable(message, ack));
        } else {
            byte[] body = new byte[message.getBodySize()];
            message.getBodyBuffer().readBytes(body);
            inflight.add(new Committable(message, ack));
            try {
                permits.acquire(); // back-pressure: block delivery until a worker slot frees
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ack.acknowledge(); // give up on this record rather than stall the queue
                trim();
                return;
            }
            try {
                pool.execute(() -> process(body, ack));
            } catch (RejectedExecutionException e) {
                // Pool is shutting down; undo the reservation and let trim() advance past this record.
                permits.release();
                ack.acknowledge();
            }
        }

        trim();
    }

    private void process(byte[] body, AckStatus ack) {
        try {
            listener.onMessage(body, ack);
        } catch (Exception e) {
            // A failing record must not stall the queue: log, ack and move on (parity with the sequential consumer).
            logger.error("{} failed to process message on topic={}, queue={}; skipping", clientId, topic, queue, e);
            ack.acknowledge();
        } finally {
            permits.release();
        }
    }

    /** Acknowledge the longest run of completed records at the head, in delivery order. Delivery thread only. */
    private void trim() {
        ClientMessage lastDone = null;
        Committable c;
        while ((c = inflight.peek()) != null && c.ack().isAcknowledged()) {
            lastDone = c.message();
            inflight.poll();
        }
        if (lastDone != null) {
            acknowledge(lastDone); // cumulative: acks every record delivered up to and including this one
            maybeCommit();
        }
    }

    @Override
    void beforeSessionClose() {
        // Delivery has stopped (the consumer is closed), so inflight is no longer touched by the delivery thread.
        // Drain the pool so every dispatched record finishes and sets its ack, then stage the final acks; the base
        // close() commits them.
        pool.shutdown();
        try {
            if (!pool.awaitTermination(SHUTDOWN_DRAIN_SECONDS, TimeUnit.SECONDS)) {
                logger.warn("{} timed out draining workers on queue={}; abandoning unfinished records", clientId, queue);
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }
        trim();
    }
}
