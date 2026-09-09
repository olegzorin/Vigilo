package dev.olegz.vf.common.monitor;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkloadMonitorTest {
    @Test
    void buildsTimeSeriesAndDropsExpiredSlices() {
        AtomicLong time = new AtomicLong(1_000L);
        WorkloadMonitor monitor = new WorkloadMonitor("test", 100L, 2, 2, time::get);

        monitor.addTaskDuration(10L);
        monitor.addTaskDuration(20L);

        time.set(1_050L);
        monitor.addTaskDuration(30L);
        time.set(1_100L);

        assertArrayEquals(new TimeSlice[] {
            new TimeSlice(1_000L, 1_050L, 2, 30L),
            new TimeSlice(1_050L, 1_100L, 1, 30L)
        }, monitor.getTimeSeries());
        assertEquals(100L, monitor.getElapsedTime());

        time.set(1_150L);

        assertArrayEquals(new TimeSlice[] {
            new TimeSlice(1_050L, 1_100L, 1, 30L),
            new TimeSlice(1_100L, 1_150L, 0, 0L)
        }, monitor.getTimeSeries());
    }
}
