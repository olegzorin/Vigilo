package dev.olegz.vf.messaging.providers.sqs;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import dev.olegz.vf.aws.sqs.SqsMessage;
import dev.olegz.vf.aws.sqs.SqsSupport;
import dev.olegz.vf.messaging.MessageListener;
import dev.olegz.vf.messaging.consumer.AckStatus;
import dev.olegz.vf.messaging.support.MessagingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SqsConsumer implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(SqsConsumer.class);

    private final String topic;
    private final String queue;
    private final MessageListener listener;
    private final String clientId;
    private final int maxPollRecords;
    private final int waitTimeSeconds;
    private final int visibilityTimeoutSeconds;
    private final ExecutorService executor;
    private final ConcurrentHashMap<String, PendingMessage> pending = new ConcurrentHashMap<>();

    private volatile boolean running;
    private Thread thread;

    SqsConsumer(String topic, String queue, MessageListener listener, String clientId, int maxPollRecords,
        int waitTimeSeconds, int visibilityTimeoutSeconds, int threadPoolSize)
    {
        this.topic = topic;
        this.queue = queue;
        this.listener = listener;
        this.clientId = clientId;
        this.maxPollRecords = maxPollRecords;
        this.waitTimeSeconds = waitTimeSeconds;
        this.visibilityTimeoutSeconds = visibilityTimeoutSeconds;
        this.executor = threadPoolSize > 1 ? Executors.newFixedThreadPool(threadPoolSize,
            MessagingUtils.threadFactory("sqs-worker-", clientId, logger)) : null;
    }

    void start() {
        if (running) {
            logger.warn("Trying to start already running consumer {}", clientId);
            return;
        }
        running = true;
        thread = new Thread(this, "sqs-consumer-" + clientId);
        thread.setUncaughtExceptionHandler((t, e) -> logger.error("Uncaught exception in {}", t.getName(), e));
        thread.start();
    }

    @Override
    public void run() {
        while (running) {
            drainPending();
            List<SqsMessage> messages = SqsSupport.receiveMessages(queue, maxPollRecords, waitTimeSeconds,
                visibilityTimeoutSeconds);
            if (messages == null || messages.isEmpty()) {
                idle();
                continue;
            }
            for (SqsMessage message : messages) {
                if (!running) break;
                process(message);
            }
        }
        drainPending();
    }

    private void process(SqsMessage message) {
        if (listener.expired(message.sentTimestamp())) {
            delete(message.receiptHandle());
            return;
        }

        AckStatus ack = new AckStatus();
        long expiresAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(visibilityTimeoutSeconds);
        pending.put(message.receiptHandle(), new PendingMessage(ack, expiresAt));
        Runnable task = () -> {
            deliver(body(message), ack);
            if (ack.isAcknowledged()) delete(message.receiptHandle());
        };

        if (executor == null) {
            task.run();
        } else {
            executor.execute(task);
        }
    }

    void deliver(byte[] message, AckStatus ack) {
        try {
            listener.onMessage(message, ack);
        } catch (Exception e) {
            if (ack.isAcknowledged()) {
                logger.error("{} failed to process message on topic={}, queue={}; skipping",
                    clientId, topic, queue, e);
            } else {
                logger.error("{} failed to process message on topic={}, queue={}; leaving it for redelivery",
                    clientId, topic, queue, e);
            }
        }
    }

    private void drainPending() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, PendingMessage> e : pending.entrySet()) {
            PendingMessage p = e.getValue();
            if (p.ack().isAcknowledged()) {
                delete(e.getKey());
            } else if (p.expiresAt() < now) {
                pending.remove(e.getKey());
            }
        }
    }

    private void delete(String receiptHandle) {
        if (pending.remove(receiptHandle) == null) return;
        SqsSupport.deleteMessage(queue, receiptHandle);
    }

    private void idle() {
        try {
            Thread.sleep(100L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    private static byte[] body(SqsMessage message) {
        try {
            return Base64.getDecoder().decode(message.body());
        } catch (IllegalArgumentException e) {
            return message.body().getBytes(StandardCharsets.UTF_8);
        }
    }

    void close() {
        running = false;
        if (thread != null) thread.interrupt();
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) executor.shutdownNow();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '[' + clientId + ", topic=" + topic + ", queue=" + queue + ']';
    }

    private record PendingMessage(AckStatus ack, long expiresAt) {
    }
}
