package dev.olegz.vf.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

class ReportDateTimeTest {
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    @Test
    void parsesAbsoluteAndRelativeReportParameters() {
        long last = ZonedDateTime.of(2026, 3, 8, 1, 30, 0, 0, NEW_YORK)
            .toInstant().toEpochMilli();

        assertEquals(last, ReportDateTime.parseReportTimestamp("{last}", last, NEW_YORK));
        assertEquals(
            ZonedDateTime.of(2026, 3, 8, 3, 30, 0, 0, NEW_YORK).toInstant().toEpochMilli(),
            ReportDateTime.parseReportTimestamp("{last}+h", last, NEW_YORK));
        assertEquals(
            ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, NEW_YORK).toInstant().toEpochMilli(),
            ReportDateTime.parseReportTimestamp("2026-03-01", last, NEW_YORK));
    }

    @Test
    void rejectsRelativeLastWithoutBaseTime() {
        assertThrows(IllegalArgumentException.class,
            () -> ReportDateTime.parseReportTimestamp("{last}-d", 0, NEW_YORK));
    }
}
