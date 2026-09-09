package dev.olegz.vf.messaging.consumer;

import dev.olegz.vf.messaging.MessageBroker;
import dev.olegz.vf.messaging.MessageListener;

/**
 * Tuning for a pooled consumer registration: how records are parallelized ({@link ConsumerMode}), the degree of
 * parallelism, and the poll limits. Bundles those arguments of
 * {@link MessageBroker#setMessageListeners(String, MessageListener, ConsumerConfig)} into one value so callers
 * compose them by name instead of positionally.
 * <p>
 * Build one from a {@link #of}/{@link #ordered}/{@link #concurrent} factory and refine it with the {@code with*}
 * methods, e.g. {@code ConsumerConfig.concurrent(10).withMaxPollRecords(5)}. Unspecified knobs default to
 * {@link MessageBroker#DEFAULT_MAX_POLL_INTERVAL} and a single record per poll.
 *
 * @param mode whether records must be processed in order or may be processed concurrently; see {@link ConsumerMode}
 * @param poolSize degree of parallelism (worker threads or parallel consumers, depending on {@code mode})
 * @param maxPollInterval maximum delay in seconds between polls before a consumer is considered failed
 * @param maxPollRecords maximum number of records to fetch in one poll
 */
public record ConsumerConfig(
    ConsumerMode mode,
    int poolSize,
    int maxPollInterval,
    int maxPollRecords)
{
    public ConsumerConfig {
        if (mode == null) mode = ConsumerMode.ORDERED;
    }

    /** Config for the given mode and parallelism, with default poll limits. */
    public static ConsumerConfig of(ConsumerMode mode, int poolSize) {
        return new ConsumerConfig(mode, poolSize, MessageBroker.DEFAULT_MAX_POLL_INTERVAL, 1);
    }

    /** Shorthand for {@link #of}{@code (}{@link ConsumerMode#ORDERED}{@code , poolSize)}. */
    public static ConsumerConfig ordered(int poolSize) {
        return of(ConsumerMode.ORDERED, poolSize);
    }

    /** Shorthand for {@link #of}{@code (}{@link ConsumerMode#CONCURRENT}{@code , poolSize)}. */
    public static ConsumerConfig concurrent(int poolSize) {
        return of(ConsumerMode.CONCURRENT, poolSize);
    }

    public ConsumerConfig withMaxPollInterval(int maxPollInterval) {
        return new ConsumerConfig(mode, poolSize, maxPollInterval, maxPollRecords);
    }

    public ConsumerConfig withMaxPollRecords(int maxPollRecords) {
        return new ConsumerConfig(mode, poolSize, maxPollInterval, maxPollRecords);
    }
}
