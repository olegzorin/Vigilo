package dev.olegz.vf.core.domain;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.util.CollectionOps;
import dev.olegz.vf.common.util.DateFormatUtils;

public class DateRange {
    // First system date 2010-01-01 00:00:00
    static final long FIRST_SYSTEM_MILLIS = 1_262_304_000_000L;
    // Last system date 2128-06-11 08:53:20.000Z
    static final long LAST_SYSTEM_MILLIS = 5_000_000_000_000L;

    public Timestamp startDate;
    public Timestamp endDate;
    public boolean edge;

    // select from the database
    private DateRange() {
    }

    public DateRange(Timestamp startDate, Timestamp endDate) {
        this.startDate = startDate != null ? startDate : new Timestamp(FIRST_SYSTEM_MILLIS);
        this.endDate = endDate != null ? endDate : Timestamp.from(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    }

    public DateRange(Timestamp startDate, Timestamp endDate, boolean edge) {
        this(startDate, endDate);
        this.edge = edge;
    }

    @Override
    public String toString() {
        return "{" + DateFormatUtils.logTimestamp(startDate) + "/" + DateFormatUtils.logTimestamp(endDate) + '}';
    }

    public static List<DateRange> fromTablePartitions(List<String> partitions) {
        int size = partitions.size();
        ArrayList<DateRange> dateRanges = new ArrayList<>(size);
        if (size == 0) return dateRanges;

        if (size > 1) Collections.sort(partitions);

        Timestamp startDate = new Timestamp(FIRST_SYSTEM_MILLIS);
        for (String partition : partitions) {
            Timestamp endDate;
            try {
                endDate = new Timestamp(DateFormatUtils.parseDate(partition.replace("'", "")));
            } catch (DateTimeParseException e) {
                throw new ApplicationFailureException("Cannot parse partition description " + partition, e);
            }

            dateRanges.add(new DateRange(startDate, endDate));
            startDate = endDate;
        }

        return Collections.unmodifiableList(dateRanges); // can be stored in memory cache
    }

    public static List<DateRange> splitRanges(List<DateRange> dateRanges, long duration, int maxNumber) {
        return CollectionOps.flatMap(dateRanges, dr -> dr.split(duration, maxNumber));
    }

    public List<DateRange> split(long duration, int maxNumber) {
        if (maxNumber < 2) return List.of(this);

        long diff = this.endDate.getTime() - this.startDate.getTime();
        long maxSize = diff / duration;
        if (maxSize * duration < diff) maxSize++;
        if (maxNumber > maxSize) maxNumber = (int)maxSize;
        if (maxNumber < 2) return List.of(this);

        ArrayList<DateRange> res = new ArrayList<>(maxNumber);
        maxNumber--;

        long time = this.endDate.getTime() - duration * maxNumber;
        Timestamp startTs = new Timestamp(time);
        res.add(new DateRange(this.startDate, startTs));

        for (int i = 1; i < maxNumber; i++) {
            time += duration;
            Timestamp newEndDate = new Timestamp(time);
            res.add(new DateRange(startTs, newEndDate));
            startTs = newEndDate;
        }

        res.add(new DateRange(startTs, this.endDate));

        return res;
    }

    public List<DateRange> intersect(List<DateRange> baseRange) {
        ArrayList<DateRange> res = new ArrayList<>(4);

        long startTime = this.startDate.getTime();
        long endTime = this.endDate.getTime();

        int baseSize = baseRange.size();
        int baseInd = 0;
        int low = 0;
        int high = baseSize - 1;

        // base list can be very long, try to find proper starting point
        while (low <= high) {
            int mid = (low + high) >>> 1;
            DateRange midVal = baseRange.get(mid);

            if (midVal.endDate.getTime() <= startTime) {
                low = mid + 1;
            } else if (midVal.startDate.getTime() > startTime) {
                high = mid - 1;
            } else {
                baseInd = mid;
                break;
            }
        }

        if (baseInd == 0) baseInd = low;

        for (; baseInd < baseSize; baseInd++) {
            DateRange base = baseRange.get(baseInd);
            if (base.endDate.getTime() <= startTime) continue;

            DateRange intersection = base.intersect(this);
            if (intersection != null) res.add(intersection);

            if ((base.startDate.getTime() >= endTime)) break;
        }

        return res;
    }

    /**
     * Intersect two ordered range arrays
     * @param selectRange small array of selected ranges
     * @param baseRange base array (e.g. table partitions)
     * @return list of intersections over base range
     */
    public static List<List<DateRange>> intersect(List<DateRange> selectRange, List<DateRange> baseRange) {
        if (baseRange.isEmpty()) return null;
        ArrayList<List<DateRange>> res = new ArrayList<>(selectRange.size() * 4);

        int nextInd = 1;
        int selSize = selectRange.size();
        DateRange currSel = selectRange.getFirst();
        long currStartTime = currSel.startDate.getTime();

        int baseSize = baseRange.size();
        int baseInd = 0;
        int low = 0;
        int high = baseSize - 1;

        // base list can be very long, try to find proper starting point
        while (low <= high) {
            int mid = (low + high) >>> 1;
            DateRange midVal = baseRange.get(mid);

            if (midVal.endDate.getTime() <= currStartTime) {
                low = mid + 1;
            } else if (midVal.startDate.getTime() > currStartTime) {
                high = mid - 1;
            } else {
                baseInd = mid;
                break;
            }
        }

        if (baseInd == 0) baseInd = low;

        for (; baseInd < baseSize; baseInd++) {
            DateRange base = baseRange.get(baseInd);
            if (base.endDate.getTime() <= currSel.startDate.getTime()) continue;

            List<DateRange> subRange = null;
            DateRange intersection = base.intersect(currSel); // check previous selected range
            if (intersection != null) {
                subRange = new ArrayList<>(4);
                res.add(subRange);
                subRange.add(intersection);
            }

            for (int i = nextInd; i < selSize; i++) {
                DateRange s = selectRange.get(i);
                if (base.endDate.getTime() <= s.startDate.getTime()) {
                    if (intersection == null) {
                        // no intersection with currSel - currSel.endDate < base.startDate
                        currSel = s;
                        nextInd = i + 1;
                    }
                    break;
                }

                currSel = s;
                nextInd = i + 1;

                intersection = base.intersect(currSel);
                if (intersection != null) {
                    if (subRange == null) {
                        subRange = new ArrayList<>(4);
                        res.add(subRange);
                    }
                    subRange.add(intersection);
                }
            }

            // check last selected range
            if ((nextInd >= selSize) && (base.startDate.getTime() >= currSel.endDate.getTime())) break;
        }

        return res;
    }

    private DateRange intersect(DateRange other) {
        if (this.includes(other)) return other;
        if (other.includes(this)) return this;

        Timestamp startDate = this.startDate.getTime() < other.startDate.getTime() ? other.startDate : this.startDate;
        Timestamp endDate = this.endDate.getTime() > other.endDate.getTime() ? other.endDate : this.endDate;

        return (startDate.getTime() < endDate.getTime()) ? new DateRange(startDate, endDate) : null;
    }

    private boolean includes(DateRange other) {
        return (this.startDate.getTime() <= other.startDate.getTime()) && (this.endDate.getTime() >= other.endDate.getTime());
    }
}
