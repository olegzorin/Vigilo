package dev.olegz.vf.common;

import java.time.Duration;

/**
 * A {@link java.util.Date} whose value is stored with one-second precision.
 * Milliseconds supplied to the constructor are truncated, which matches
 * the precision of the database columns used for application datetimes.
 */
public class Datetime extends java.util.Date {

    public Datetime(long time) {
        super((time / 1000) * 1000);
    }

    public static Datetime now() {
        return new Datetime(System.currentTimeMillis());
    }
    public static Datetime nowPlusDays(int days) {
        return nowPlus(Duration.ofDays(days));
    }
    public static Datetime nowMinusDays(int days) {
        return nowMinus(Duration.ofDays(days));
    }

    public static Datetime nowPlus(Duration duration) {
        return new Datetime(System.currentTimeMillis() + duration.toMillis());
    }

    public static Datetime nowMinus(Duration duration) {
        return new Datetime(System.currentTimeMillis() - duration.toMillis());
    }
}
