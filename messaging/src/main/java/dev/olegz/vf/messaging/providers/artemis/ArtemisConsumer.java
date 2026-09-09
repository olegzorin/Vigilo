package dev.olegz.vf.messaging.providers.artemis;

import dev.olegz.vf.messaging.MessageListener;
import dev.olegz.vf.messaging.MessagingException;
import dev.olegz.vf.messaging.consumer.AckStatus;
import org.apache.activemq.artemis.api.core.*;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A single Artemis consumer bound to one queue, delivering records to a {@link MessageListener} sequentially on the
 * session's own delivery thread. The broker registers one of these for a single-thread listener, and {@code poolSize}
 * of them competing on a shared queue for an {@link dev.olegz.vf.messaging.consumer.ConsumerMode#ORDERED} pooled
 * listener &mdash; there message grouping pins each key to one consumer, so per-key order is preserved.
 * ({@link dev.olegz.vf.messaging.consumer.ConsumerMode#CONCURRENT} instead uses {@link ArtemisPoolConsumer}, which
 * processes records on a thread pool and so does not preserve order.)
 * <p>
 * Records are processed in the push {@link MessageHandler} callback, which Artemis invokes sequentially per
 * consumer. Each record is acknowledged once its listener returns and the staged acks are flushed with
 * {@code session.commit()} on the {@code maxCommitInterval} cadence (gated by {@link MessageListener#commit(long)}),
 * giving the same at-least-once behaviour as the Kafka consumer's periodic offset commit. {@link ArtemisPoolConsumer}
 * extends this class, reusing its session lifecycle, acknowledgement and commit logic.
 */
class ArtemisConsumer implements MessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(ArtemisConsumer.class);

    private final ArtemisMessageBroker broker;
    final String topic;
    final String queue;
    private final RoutingType routingType;
    final MessageListener listener;
    final String clientId;
    private final long maxCommitInterval;

    ClientSession session;
    private ClientConsumer consumer;
    volatile boolean running;

    // Serializes every session call (acknowledge/commit/close), which the Artemis ClientSession does not allow
    // concurrently. Paired with 'closed' so a delivery-thread ack/commit racing close() turns into a no-op (the
    // record simply redelivers) instead of touching a session being torn down.
    private final Object sessionLock = new Object();
    private boolean closed; // guarded by sessionLock

    // written under sessionLock in commit(); read on the delivery thread
    private long lastCommitTime;

    ArtemisConsumer(ArtemisMessageBroker broker, String topic, String queue, RoutingType routingType,
        MessageListener listener, String clientId, long maxCommitInterval)
    {
        this.broker = broker;
        this.topic = topic;
        this.queue = queue;
        this.routingType = routingType;
        this.listener = listener;
        this.clientId = clientId;
        this.maxCommitInterval = maxCommitInterval;
    }

    void start() {
        if (running) {
            logger.warn("Trying to start already running consumer {}", clientId);
            return;
        }
        try {
            session = broker.createConsumerSession();
            // ANYCAST work queues are durable (records persist, parity with Kafka's log); MULTICAST per-server
            // queues are live-only broadcasts that need no persistence.
            boolean durable = routingType == RoutingType.ANYCAST;
            ensureQueue(session, topic, queue, routingType, durable);

            consumer = session.createConsumer(SimpleString.of(queue));
            consumer.setMessageHandler(this);
            lastCommitTime = System.currentTimeMillis();
            running = true;
            session.start();
        } catch (ActiveMQException e) {
            close();
            throw new MessagingException("Cannot start Artemis consumer " + clientId + " on queue=" + queue, e);
        }
    }

    @Override
    public void onMessage(ClientMessage message) {
        if (!running) return;

        if (listener.expired(message.getTimestamp())) {
            acknowledge(message);
            maybeCommit();
            return;
        }

        byte[] body = new byte[message.getBodySize()];
        message.getBodyBuffer().readBytes(body);

        AckStatus ack = new AckStatus();
        try {
            listener.onMessage(body, ack);
        } catch (Exception e) {
            // A failing record must not stall the queue: log, ack and move on (parity with the Kafka consumer).
            logger.error("{} failed to process message on topic={}, queue={}; skipping", clientId, topic, queue, e);
            ack.acknowledge();
        }

        if (ack.isAcknowledged()) {
            acknowledge(message);
            maybeCommit();
        }
    }

    void acknowledge(ClientMessage message) {
        synchronized (sessionLock) {
            if (closed) return;
            try {
                message.acknowledge();
            } catch (ActiveMQException e) {
                logger.warn("{} failed to acknowledge on queue={}", clientId, queue, e);
            }
        }
    }

    void maybeCommit() {
        long now = System.currentTimeMillis();
        if (maxCommitInterval <= 0 || lastCommitTime + maxCommitInterval < now) {
            commit(now);
        }
    }

    private void commit(long now) {
        if (!listener.commit(now)) return;
        synchronized (sessionLock) {
            if (closed) return;
            try {
                session.commit();
                lastCommitTime = now;
            } catch (ActiveMQException e) {
                logger.warn("{} commit failed on queue={}", clientId, queue, e);
            }
        }
    }

    void close() {
        running = false;
        // Stop delivery first; this returns once any in-progress onMessage has finished, so no record is processed
        // past this point. Done outside sessionLock: consumer.close() may itself wait on the delivery thread, which
        // can be holding the lock for an ack/commit, and holding it here would deadlock.
        try {
            if (consumer != null) consumer.close();
        } catch (Exception e) {
            logger.warn("Exception closing consumer {} on queue={}", clientId, queue, e);
        }
        // Subclass hook: drain in-flight work and stage its acks now that no more records will be delivered, so the
        // final commit() below flushes them. No-op for the sequential consumer, which acks inline.
        beforeSessionClose();
        synchronized (sessionLock) {
            if (closed) return;
            closed = true; // any later ack/commit from a straggling delivery becomes a no-op
            if (session != null) {
                try {
                    session.commit(); // flush any acks staged since the last commit
                } catch (Exception e) {
                    logger.warn("Exception in final commit of {} on queue={}", clientId, queue, e);
                }
                try {
                    session.close();
                } catch (Exception e) {
                    logger.warn("Exception closing session of {} on queue={}", clientId, queue, e);
                }
            }
        }
    }

    /** Hook run during {@link #close()} after delivery stops and before the session is committed and closed. */
    void beforeSessionClose() {
    }

    /** Idempotently create the address and the queue bound to it; tolerate concurrent/repeat creation. */
    private static void ensureQueue(ClientSession session, String address, String queue, RoutingType rt, boolean durable)
        throws ActiveMQException
    {
        SimpleString addr = SimpleString.of(address);
        try {
            session.createAddress(addr, rt, true);
        } catch (ActiveMQAddressExistsException ignore) {
            // address already present
        }
        try {
            session.createQueue(QueueConfiguration.of(queue)
                .setAddress(addr)
                .setRoutingType(rt)
                .setDurable(durable));
        } catch (ActiveMQQueueExistsException ignore) {
            // queue already present
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '[' + clientId + ", topic=" + topic + ", queue=" + queue + ']';
    }
}
