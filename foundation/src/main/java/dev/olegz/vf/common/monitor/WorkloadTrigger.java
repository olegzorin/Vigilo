package dev.olegz.vf.common.monitor;

import java.util.function.BiFunction;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A workload-based trigger that executes an action when a condition is met based on event value and duration.
 * The trigger evaluates a condition function with the current value and duration, and executes the configured
 * action if the condition returns true. Includes a silence period mechanism to prevent repeated triggering
 * within a specified time window after the last successful execution.
 *
 * Thread-safe: uses double-checked locking on the action object to ensure the action runs only once
 * even when multiple threads attempt to trigger it simultaneously within the silence period.
 */
public class WorkloadTrigger {
    private static final Logger logger = LoggerFactory.getLogger(WorkloadTrigger.class);

    private final BiFunction<Integer, Long, Boolean> condition;
    private final Runnable action;
    private final long silenceTime;
    private final LongSupplier currentTimeMillis;

    private volatile long lastTriggerTime;


    public WorkloadTrigger(BiFunction<Integer, Long, Boolean> condition, Runnable action, long silenceTime) {
        this(condition, action, silenceTime, System::currentTimeMillis);
    }

    WorkloadTrigger(BiFunction<Integer, Long, Boolean> condition, Runnable action, long silenceTime,
        LongSupplier currentTimeMillis)
    {
        this.condition = condition;
        this.action = action;
        this.silenceTime = silenceTime;
        this.currentTimeMillis = currentTimeMillis;
    }

    void check(int value, long duration) {
        if (!condition.apply(value, duration)) return;

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
