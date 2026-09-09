package dev.olegz.vf.common.monitor;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.util.CollectionOps;

/**
 * A thread-safe event rate monitoring system that tracks event counts over a sliding time window
 * and triggers actions when configurable thresholds are exceeded.
 * <p>
 * This monitor divides the configured time window into multiple time slices for granular tracking
 * of event rates. It uses a branch-based counting strategy to minimize thread contention when
 * recording events from multiple threads simultaneously. Each branch maintains its own atomic counter,
 * and branches are periodically synchronized when time slices advance.
 * <p>
 * The sliding time window automatically advances as time progresses, dropping expired slices and
 * creating new ones. Events are aggregated across all slices to provide a total count over the
 * monitored time window. When events are added, all configured triggers are evaluated against
 * the current event count.
 * <p>
 * Thread Safety:
 * The monitor is designed for high-concurrency scenarios where multiple threads simultaneously
 * record events. Thread contention is reduced by partitioning event counts across multiple branches,
 * with each thread consistently using the same branch based on its thread ID. Time slice management
 * is protected by synchronization to ensure consistent state when transitioning between time windows.
 * <p>
 * Time Slice Management:
 * The time window is divided into a configurable number of slices, each representing a portion
 * of the total monitoring period. As the current time advances beyond a slice boundary, the monitor
 * creates a new slice containing events from the just-completed period, adds it to the historical
 * record, and removes slices that have expired beyond the time window. This maintains a constant
 * window size while allowing the window to slide forward in time.
 * <p>
 * Branch Synchronization:
 * Each branch maintains a count that includes both historical events (from past slices) and recent
 * events recorded since the last slice boundary. When a new slice is created, all branches are
 * synchronized to reflect the updated historical baseline, ensuring consistent counting across
 * all threads.
 *
 * @see EventRateTrigger
 * @see TimeSlice
 */
public final class EventRateMonitor {
    private final long timeStep;
    private final long timeWindow;
    private final EventRateTrigger[] triggers;
    private final LongSupplier currentTimeMillis;
    private final LinkedList<TimeSlice> pastSlices = new LinkedList<>();
    private volatile long currentSliceEndTime;

    // Each branch contains the total from pastSlices plus
    // the number of recent events that came from part of the threads.
    // Branches are synchronized every time new time slice is added to pastSlices.
    private final AtomicInteger[] branches;

    private long computeEndTime() {
        return timeStep * (currentTimeMillis.getAsLong() / timeStep) + timeStep;
    }

    /**
     * Creates a new monitor with the specified configuration.
     *
     * @param timeWindow the total time window for tracking events in milliseconds
     * @param numOfSlices the number of slices to divide the time window into for granularity
     * @param numOfBranches the number of branches for reducing thread contention (typically matches expected thread count)
     * @param triggers the rate triggers to invoke when events are added
     * @throws ApplicationFailureException if no triggers are provided
     */
    public EventRateMonitor(long timeWindow, int numOfSlices, int numOfBranches, EventRateTrigger... triggers) {
        this(timeWindow, numOfSlices, numOfBranches, System::currentTimeMillis, triggers);
    }

    EventRateMonitor(long timeWindow, int numOfSlices, int numOfBranches, LongSupplier currentTimeMillis,
        EventRateTrigger... triggers)
    {
        if (triggers.length == 0) throw new ApplicationFailureException("Useless to launch the monitor without triggers");
        this.timeStep = timeWindow / numOfSlices;
        this.timeWindow = numOfSlices * timeStep;
        this.currentTimeMillis = currentTimeMillis;
        this.branches = new AtomicInteger[numOfBranches];
        for (int i = 0; i < numOfBranches; i++) {
            this.branches[i] = new AtomicInteger(0);
        }
        this.triggers = triggers;
        this.currentSliceEndTime = computeEndTime();
    }

    /**
     * Records an event and checks all configured triggers.
     * Updates the sliding time window, removing expired slices and adding new ones as needed.
     * Thread-safe: uses branch-based counting to minimize contention across threads.
     */
    public void addEvent() {
        long time = currentTimeMillis.getAsLong();
        if (time >= currentSliceEndTime) {
            synchronized (pastSlices) {
                if (time >= currentSliceEndTime) {
                    int pastCount = CollectionOps.sumIntValues(pastSlices, TimeSlice::count);
                    int lastCount = CollectionOps.sumIntValues(List.of(branches), AtomicInteger::get) - branches.length * pastCount;

                    pastSlices.addLast(new TimeSlice(currentSliceEndTime - timeStep, currentSliceEndTime, lastCount, 0L));

                    for (var branch : branches) {
                        branch.set(pastCount + lastCount);
                    }

                    currentSliceEndTime = computeEndTime();

                    long minEndTime = currentSliceEndTime - timeWindow;
                    while (!pastSlices.isEmpty() && (pastSlices.getFirst().endTime() < minEndTime)) {
                        TimeSlice removed = pastSlices.removeFirst();
                        for (var branch : branches) {
                            branch.addAndGet(-removed.count());
                        }
                    }
                }
            }
        }

        int branchIndex = (int) (Thread.currentThread().threadId() % branches.length);
        int count = branches[branchIndex].incrementAndGet();

        for (var trigger : triggers) trigger.check(count);
    }
}
