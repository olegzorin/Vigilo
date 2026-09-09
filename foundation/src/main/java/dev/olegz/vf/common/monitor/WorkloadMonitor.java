package dev.olegz.vf.common.monitor;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A thread-safe workload monitor that tracks tasks and their durations over a sliding time window.
 * <p>
 * This monitor divides the configured time window into multiple time slices for granular tracking
 * and uses multiple branches to reduce thread contention when recording tasks. Events older than
 * the time window are automatically discarded as time progresses.
 * <p>
 * The monitor maintains a sliding window of time slices and supports optional triggers that can
 * be evaluated when tasks are added. Each task consists of a duration value accumulated
 * along with the task count.
 * <p>
 * Thread Safety:
 * The implementation uses branch-based counting to minimize contention across threads. Each thread
 * writes to a specific branch based on its thread ID, and branches are synchronized when the time
 * window advances.
 * <p>
 * Time Window Management:
 * The time window is divided into a configurable number of slices. As time progresses, expired
 * slices are removed from the beginning of the window, and new slices are added at the end.
 * The monitor automatically handles the transition between time slices.
 */
public class WorkloadMonitor {
    private static final Logger logger = LoggerFactory.getLogger(WorkloadMonitor.class);

    public final String name;
    private final long timeStep;
    private final long timeWindow;
    private final WorkloadTrigger[] triggers;
    private final LongSupplier currentTimeMillis;
    private final LinkedList<TimeSlice> pastSlices = new LinkedList<>();
    private final long monitorStartTime;
    private volatile long currentSliceEndTime;

    /**
     * A thread-local accumulator branch that tracks task count and total duration.
     * Multiple branches reduce contention by distributing writes across threads based on thread ID.
     */
    private static class Branch {
        private final AtomicInteger totalCount = new AtomicInteger(0);
        private final AtomicLong totalDuration = new AtomicLong(0L);
    }

    // Each branch contains the total from pastSlices plus
    // data of recent tasks that came from part of the threads.
    // Branches are synchronized every time new time slice is added to pastSlices.
    private final Branch[] branches;

    /**
     * Creates a new load monitor with the specified configuration.
     *
     * @param name the name of this monitor, used for logging
     * @param timeWindow the total time window for tracking tasks in milliseconds
     * @param numOfSlices the number of slices to divide the time window into for granularity
     * @param numOfBranches the number of branches for reducing thread contention (typically matches expected thread count)
     * @param triggers optional load triggers to invoke when tasks are added
     */
    public WorkloadMonitor(String name, long timeWindow, int numOfSlices, int numOfBranches, WorkloadTrigger... triggers) {
        this(name, timeWindow, numOfSlices, numOfBranches, System::currentTimeMillis, triggers);
    }

    WorkloadMonitor(String name, long timeWindow, int numOfSlices, int numOfBranches, LongSupplier currentTimeMillis,
        WorkloadTrigger... triggers)
    {
        this.name = name;
        this.timeStep = timeWindow / numOfSlices;
        this.timeWindow = numOfSlices * timeStep;
        this.currentTimeMillis = currentTimeMillis;
        this.branches = new Branch[numOfBranches];
        for (int i = 0; i < numOfBranches; i++) this.branches[i] = new Branch();
        this.monitorStartTime = currentTimeMillis.getAsLong();
        this.currentSliceEndTime = monitorStartTime + timeStep;
        this.triggers = triggers.length > 0 ? triggers : null;
    }

    /**
     * Records an task with the specified duration and checks all configured triggers.
     * Updates the sliding time window, removing expired slices and adding new ones as needed.
     * Thread-safe: uses branch-based counting to minimize contention across threads.
     *
     * @param taskDuration the duration of the task in milliseconds
     */
    public void addTaskDuration(long taskDuration) {
        update();
        int branchIndex = (int) (Thread.currentThread().threadId() % branches.length);
        int count = branches[branchIndex].totalCount.incrementAndGet();
        long duration = branches[branchIndex].totalDuration.addAndGet(taskDuration);
        if (triggers != null) {
            for (var trigger : triggers) trigger.check(count, duration);
        }
    }

    /**
     * Updates the sliding time window by creating new time slices and removing expired ones.
     * Uses double-checked locking: returns immediately if current slice hasn't ended,
     * otherwise synchronizes to aggregate branch data into a new slice, reset branches,
     * advance the window, and discard slices older than the time window.
     */
    private void update() {
        long currentTime = currentTimeMillis.getAsLong();
        if (currentTime < currentSliceEndTime) return;

        synchronized (pastSlices) {
            if (currentTime < currentSliceEndTime) return;

            int pastCount = 0;
            long pastDuration = 0L;
            for (TimeSlice ts : pastSlices) {
                pastCount += ts.count();
                pastDuration += ts.duration();
            }
            int lastCount = 0;
            long lastDuration = 0;
            for (Branch branch : branches) {
                lastCount += branch.totalCount.get() - pastCount;
                lastDuration += branch.totalDuration.get() - pastDuration;
            }
            pastSlices.addLast(new TimeSlice(currentSliceEndTime - timeStep, currentSliceEndTime, lastCount, lastDuration));
            if (logger.isDebugEnabled()) {
                logger.debug(name + " update() appended " + pastSlices.getLast());
            }
            for (Branch branch : branches) {
                branch.totalCount.set(pastCount + lastCount);
                branch.totalDuration.set(pastDuration + lastDuration);
            }

            while (currentTime >= currentSliceEndTime) currentSliceEndTime += timeStep;

            long minEndTime = currentSliceEndTime - timeWindow;
            while (!pastSlices.isEmpty() && (pastSlices.getFirst().endTime() < minEndTime)) {
                TimeSlice removed = pastSlices.removeFirst();
                for (Branch branch : branches) {
                    branch.totalCount.addAndGet(-removed.count());
                    branch.totalDuration.addAndGet(-removed.duration());
                }
                if (logger.isDebugEnabled()) {
                    logger.debug(name + " update() removed " + removed);
                }
            }
        }
    }

    /**
     * Returns a chronologically ordered array of time slices covering the time window.
     * Empty slices (with zero count and duration) are created for time periods with no tasks.
     *
     * @return an array of time slices from oldest to newest, covering the configured time window
     */
    public TimeSlice[] getTimeSeries() {
        update();
        final TimeSlice[] timeSlices;
        long endTime;
        synchronized (pastSlices) {
            timeSlices = pastSlices.toArray(TimeSlice[]::new);
            endTime = this.currentSliceEndTime - timeStep;
        }

        long startTime = Math.max(monitorStartTime, endTime - timeWindow);
        if (logger.isDebugEnabled()) {
            logger.debug(name + " getTimeSeries() startTime=" + startTime + ", endTime=" + endTime + ", slices: " + Arrays.toString(timeSlices));
        }

        // place the time slices chronologically
        TimeSlice[] timeSeries = new TimeSlice[(int) ((endTime - startTime) / timeStep)];
        for (TimeSlice slice : timeSlices) {
            int index = (int) ((slice.startTime() - startTime) / timeStep);
            timeSeries[index] = slice;
        }
        // fill time gaps
        for (int i = 0; i < timeSeries.length; i++) {
            if (timeSeries[i] == null) {
                long iStartTime = startTime + i * timeStep;
                timeSeries[i] = new TimeSlice(iStartTime, iStartTime + timeStep, 0, 0);
            }
        }
        return timeSeries;
    }

    /**
     * Returns the time elapsed since this monitor was created.
     *
     * @return elapsed time in milliseconds
     */
    public long getElapsedTime() {
        return currentTimeMillis.getAsLong() - monitorStartTime;
    }
}
