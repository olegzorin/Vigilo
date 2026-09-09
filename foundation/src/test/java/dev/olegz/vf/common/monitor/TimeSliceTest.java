package dev.olegz.vf.common.monitor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeSliceTest {
    @Test
    void computesAverageDuration() {
        assertEquals(25L, new TimeSlice(0L, 100L, 4, 100L).avgDuration());
        assertEquals(0L, new TimeSlice(0L, 100L, 0, 100L).avgDuration());
        assertEquals(0L, new TimeSlice(0L, 100L, 4, 0L).avgDuration());
    }
}
