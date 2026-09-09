package dev.olegz.vf.common.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for date and time formatting
 */
public class DateFormatUtils {
    private static final ZoneId DEFAULT_ZONE_ID = ZoneOffset.UTC;
    public static final TimeZone DEFAULT_TIMEZONE = TimeZone.getTimeZone(DEFAULT_ZONE_ID);
    public static final String DEFAULT_TIMEZONE_ID = DEFAULT_TIMEZONE.getID();

    private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT).withZone(DEFAULT_ZONE_ID);

    private static final DateTimeFormatter logDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC).withLocale(Locale.US);

    public static String logTimestamp(long time) {
        return logDateTimeFormatter.format(Instant.ofEpochMilli(time).atZone(ZoneOffset.UTC));
    }

    public static String logTimestamp(Date date) {
        return date == null ? "null" : logTimestamp(date.getTime());
    }

    private static final ConcurrentHashMap<ZoneId, DateTimeFormatter> dateTimePrinters = new ConcurrentHashMap<>();
    private static final DateTimeFormatter defaultDateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);

    public static String printDateTime(long time, ZoneId zoneId) {
        return zoneId == null ? defaultDateTimeFormatter.format(Instant.ofEpochMilli(time)) :
            dateTimePrinters.computeIfAbsent(zoneId, DateTimeFormatter.ISO_OFFSET_DATE_TIME::withZone).format(Instant.ofEpochMilli(time));
    }

    public static String printDateTime(long time) {
        return defaultDateTimeFormatter.format(Instant.ofEpochMilli(time));
    }

    public static long parseDate(String date) throws DateTimeParseException {
        return parseDateZoned(date).toEpochSecond() * 1000L;
    }

    public static ZonedDateTime parseDateZoned(String date) throws DateTimeParseException {
        return LocalDate.parse(date, DATE_FORMAT).atStartOfDay(DEFAULT_ZONE_ID);
    }
}
