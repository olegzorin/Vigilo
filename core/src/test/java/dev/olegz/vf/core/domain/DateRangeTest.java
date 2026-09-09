package dev.olegz.vf.core.domain;

import java.sql.Timestamp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class DateRangeTest {

    @Test
    void defaultStartDatesAreIndependent() {
        DateRange first = new DateRange(null, new Timestamp(2_000_000_000_000L));
        DateRange second = new DateRange(null, new Timestamp(2_000_000_000_000L));

        assertNotSame(first.startDate, second.startDate);

        first.startDate.setTime(0);
        assertEquals(DateRange.FIRST_SYSTEM_MILLIS, second.startDate.getTime());
    }
}
