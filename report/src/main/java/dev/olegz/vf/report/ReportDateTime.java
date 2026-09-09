package dev.olegz.vf.report;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Date expressions supported by CareDaily report parameters. */
public final class ReportDateTime {
    public static final ZoneId DEFAULT_ZONE_ID = ZoneOffset.UTC;

    private static final Pattern RELATIVE_TIME = Pattern.compile("^([+-])(\\d{0,2})([hdwm])$");
    private static final DateTimeFormatter ISO_OFFSET_DATE = new DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .append(DateTimeFormatter.ISO_OFFSET_DATE)
        .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
        .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
        .toFormatter();

    private ReportDateTime() {
    }

    public static long parseReportTimestamp(String value, long lastTime, ZoneId zoneId) {
        if (value == null) return 0;
        ZoneId effectiveZone = zoneId == null ? DEFAULT_ZONE_ID : zoneId;
        if (value.startsWith("{now}")) {
            long now = currentTimeMillis();
            return value.length() == 5 ? now : parseRelativeTime(now, value.substring(5), effectiveZone);
        }
        if (value.startsWith("{last}")) {
            if (lastTime == 0) throw new IllegalArgumentException("Last time is not set");
            return value.length() == 6 ? lastTime : parseRelativeTime(lastTime, value.substring(6), effectiveZone);
        }
        return parseTime(value, effectiveZone);
    }

    public static long currentTimeMillis() {
        long time = System.currentTimeMillis();
        return time - time % 1000L;
    }

    private static long parseRelativeTime(long baseTime, String value, ZoneId zoneId) {
        Matcher matcher = RELATIVE_TIME.matcher(value);
        if (value.length() > 4 || !matcher.matches()) {
            throw new IllegalArgumentException("Unrecognized relative time string: " + value);
        }
        int amount = (matcher.group(2).isEmpty() ? 1 : Integer.parseInt(matcher.group(2)))
            * (matcher.group(1).equals("+") ? 1 : -1);
        ZonedDateTime base = Instant.ofEpochMilli(baseTime).atZone(zoneId);
        return (switch (matcher.group(3).charAt(0)) {
            case 'h' -> base.plusHours(amount);
            case 'd' -> base.plusDays(amount);
            case 'w' -> base.plusWeeks(amount);
            case 'm' -> base.plusMonths(amount);
            default -> throw new IllegalArgumentException("Unrecognized relative time string: " + value);
        }).toEpochSecond() * 1000L;
    }

    private static long parseTime(String value, ZoneId zoneId) {
        try {
            long time = Long.parseLong(value);
            return time - time % 1000L;
        } catch (NumberFormatException ignored) {
            OffsetDateTime dateTime = parseOffsetDateTime(value, zoneId);
            if (dateTime == null) throw new IllegalArgumentException("Invalid date format " + value);
            return dateTime.toEpochSecond() * 1000L;
        }
    }

    private static OffsetDateTime parseOffsetDateTime(String value, ZoneId zoneId) {
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value, ISO_OFFSET_DATE);
            } catch (DateTimeParseException ignoredAgain) {
                LocalDateTime local;
                try {
                    local = LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                } catch (DateTimeParseException ignoredDateTime) {
                    try {
                        local = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
                    } catch (DateTimeParseException ignoredDate) {
                        return null;
                    }
                }
                return OffsetDateTime.of(local, zoneId.getRules().getOffset(local));
            }
        }
    }
}
