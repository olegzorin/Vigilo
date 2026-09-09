package dev.olegz.vf.common.monitor;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A thread-safe trigger mechanism that executes an action when an event rate exceeds a specified threshold,
 * with built-in rate limiting to prevent excessive triggering.
 * <p>
 * This class monitors a numeric value against a configurable threshold and triggers an action when the value
 * exceeds that threshold. Once triggered, the action will not be executed again until the silence period has elapsed,
 * preventing rapid consecutive executions.
 * <p>
 * The trigger employs double-checked locking to ensure thread-safe execution of the action while minimizing
 * synchronization overhead. Any exceptions thrown by the action are caught and logged, preventing them from
 * propagating to the caller.
 * <p>
 * Typical use cases include:
 * - Triggering alerts when event counts exceed acceptable limits
 * - Throttling responses to high-rate events
 * - Implementing circuit breakers based on event thresholds
 *
 * @see TimeSlice
 */
public class EventRateTrigger {
    private static final Logger logger = LoggerFactory.getLogger(EventRateTrigger.class);

    private final Supplier<Integer> countThreshold;
    private final Runnable action;
    private final long silenceTime;
    private final LongSupplier currentTimeMillis;

    private volatile long lastTriggerTime;

    public EventRateTrigger(Supplier<Integer> countThreshold, Runnable action, long silenceTime) {
        this(countThreshold, action, silenceTime, System::currentTimeMillis);
    }

    EventRateTrigger(Supplier<Integer> countThreshold, Runnable action, long silenceTime, LongSupplier currentTimeMillis) {
        this.countThreshold = countThreshold;
        this.action = action;
        this.silenceTime = silenceTime;
        this.currentTimeMillis = currentTimeMillis;
    }

    void check(int value) {
        if (value <= countThreshold.get()) return;

        long time = currentTimeMillis.getAsLong() - silenceTime;
        if (time < lastTriggerTime) return;

        synchronized (action) {
            if (time < lastTriggerTime) return;
            try {
                action.run();
            } catch (Exception e) {
                logger.error("Exception while running action", e);
            }
            lastTriggerTime = currentTimeMillis.getAsLong();
        }
    }
}
