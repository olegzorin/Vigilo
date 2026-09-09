package dev.olegz.vf.common.monitor;

/**
 * Represents a time slice containing aggregated event/task metrics within a specific time range.
 *
 * @param startTime the start timestamp of this slice in milliseconds
 * @param endTime the end timestamp of this slice in milliseconds
 * @param count the number of events/tasks recorded in this slice
 * @param duration the total duration of all tasks in this slice in milliseconds
 */
public record TimeSlice(long startTime, long endTime, int count, long duration) {
    public long avgDuration() {
        return (count == 0) || (duration == 0) ? 0L : (duration / count);
    }
}
