package dev.olegz.vf.messaging.support;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import dev.olegz.vf.common.RuntimeIdentity;
import dev.olegz.vf.messaging.MessageBroker;
import dev.olegz.vf.messaging.MessageListener;
import dev.olegz.vf.messaging.consumer.ConsumerGroupScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helpers shared by the broker implementations ({@code kafka}, {@code artemis}) for naming clients/consumer groups
 * and normalising consumer-config values. Stateless apart from the per-listener group counter.
 */
public final class MessagingUtils {
    private static final Logger logger = LoggerFactory.getLogger(MessagingUtils.class);

    // Unique within the JVM across all brokers; only one broker is ever loaded, so a single counter is enough.
    private static final AtomicInteger listenerGroupIndex = new AtomicInteger(0);

    private MessagingUtils() {
    }

    /**
     * Derive a stable, log-friendly client name from a listener's class. Anonymous listeners get a synthesized name
     * (callers still make the full client id unique by appending their own counter); synthetic (lambda) class names
     * are sanitized of {@code $} and {@code /}.
     */
    public static String makeClientName(MessageListener listener) {
        String name = listener.getClass().getSimpleName();
        if (name.isEmpty()) {
            name = "RND" + ThreadLocalRandom.current().nextInt(9999);
        } else if (listener.getClass().isSynthetic()) {
            // need to sanitize the names like KafkaMessageBroker$$Lambda$1/0x...
            name = name.replace('$', '-').replace('/', '-');
        }
        return name;
    }

    /**
     * Name the consumer group (Kafka) / queue (Artemis) for a topic and scope:
     * <ul>
     *   <li>{@link ConsumerGroupScope#SHARED} &rarr; the topic name, so consumers compete and each record is handled once.</li>
     *   <li>{@link ConsumerGroupScope#PER_SERVER} &rarr; prefixed with the node id, so every node gets its own copy.</li>
     *   <li>{@link ConsumerGroupScope#PER_LISTENER} &rarr; prefixed with the JVM instance id and a counter, so every
     *       listener registration (even several in one JVM) gets its own copy.</li>
     * </ul>
     */
    public static String makeGroupName(String topic, ConsumerGroupScope consumerGroupScope) {
        return switch (consumerGroupScope) {
            case SHARED -> topic;
            case PER_SERVER -> RuntimeIdentity.NODE_ID + '-' + topic;
            case PER_LISTENER -> RuntimeIdentity.INSTANCE_ID + '-' + topic + '-' + listenerGroupIndex.getAndIncrement();
        };
    }

    /** Validate the configured max poll interval (seconds) and convert it to milliseconds. */
    public static int makeMaxPollIntervalMs(int maxPollInterval, String clientId) {
        if (maxPollInterval <= 0) {
            logger.error("Invalid maxPollInterval={} for {}", maxPollInterval, clientId);
            maxPollInterval = MessageBroker.DEFAULT_MAX_POLL_INTERVAL;
        }
        return maxPollInterval * 1000;
    }

    /** Validate the configured max poll records, falling back to 1. */
    public static int checkMaxPollRecords(int maxPollRecords, String clientId) {
        if (maxPollRecords <= 0) {
            logger.error("Invalid maxPollRecords={} for {}", maxPollRecords, clientId);
            maxPollRecords = 1;
        }
        return maxPollRecords;
    }

    /** Bound the commit interval below {@code max.poll.interval.ms}, defaulting to half of it (min 1s). */
    public static long makeMaxCommitInterval(long maxCommitInterval, int maxPollIntervalMs) {
        return maxCommitInterval > 0 ?
            Math.min(maxCommitInterval, maxPollIntervalMs) :
            maxPollIntervalMs < 2000 ? 1000 : maxPollIntervalMs / 2;
    }

    /**
     * A thread factory whose threads are named {@code <namePrefix><clientId>-<n>} and that logs uncaught exceptions
     * through the supplied logger.
     */
    public static ThreadFactory threadFactory(String namePrefix, String clientId, Logger workerLogger) {
        AtomicInteger index = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, namePrefix + clientId + '-' + index.getAndIncrement());
            t.setUncaughtExceptionHandler((thread, e) -> workerLogger.error("Uncaught exception in worker {}", thread.getName(), e));
            return t;
        };
    }
}
