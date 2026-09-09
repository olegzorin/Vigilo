package dev.olegz.vf.core.domain;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import dev.olegz.vf.common.util.CollectionOps;

/**
 *  Support for pagination
 */
public class ResultList<I extends ResultList.Item> {
    public final List<List<I>> results;
    public final int size;
    public final String nextMarker;
    public Map<String, Object> params;

    public ResultList(List<I> results) {
        if ((results != null) && !results.isEmpty()) {
            this.results = List.of(results);
            this.size = results.size();
        } else {
            this.results = null;
            this.size = 0;
        }
        this.nextMarker = null;
    }

    public ResultList(List<List<I>> results, int size, String nextMarker) {
        if ((results == null) || results.isEmpty() || (size == 0)) {
            this.results = null;
            this.size = 0;
        } else {
            this.results = results;
            this.size = size;
        }
        this.nextMarker = nextMarker;
    }

    @Override
    public String toString() {
        return "{size=" + size + ", nextMarker=" + nextMarker + '}';
    }

    public int countSize() {
        int s = 0;
        if (results != null) {
            for (List<I> r : results) s += r.size();
        }
        return s;
    }

    public interface Item {
        Timestamp ts();
        int id();

        default String asString() {
            return Long.toHexString(ts().getTime()) + '-' + Integer.toHexString(id());
        }
    }

    public static <I extends Item> ResultList<I> get(Function<DateRange, List<I>> selector, List<DateRange> dateRanges, int limit, boolean reverseOrder, String markerString) {
        long markerTime = 0;
        int markerId = 0;

        if (markerString != null && !markerString.isBlank()) {
            String[] parts = markerString.split("-", -1);
            if (parts.length == 2) {
                try {
                    markerTime = Long.parseLong(parts[0], 16);
                    markerId = Integer.parseInt(parts[1], 16);

                    if ((markerTime < DateRange.FIRST_SYSTEM_MILLIS) || (markerTime > DateRange.LAST_SYSTEM_MILLIS)) markerTime = 0;
                } catch (NumberFormatException ignore) {
                }
            }

            if (markerTime > DateRange.FIRST_SYSTEM_MILLIS) {
                long mrTime = markerTime;
                dateRanges = reverseOrder ?
                    CollectionOps.map(dateRanges, dr -> {
                        if ((dr.startDate != null) && (dr.startDate.getTime() > mrTime)) return null;
                        if ((dr.endDate == null) || (dr.endDate.getTime() > mrTime)) return new DateRange(dr.startDate, new Timestamp(mrTime + 1000L), true);
                        return dr;
                    }) :
                    CollectionOps.map(dateRanges, dr -> {
                        if ((dr.endDate != null) && (dr.endDate.getTime() <= mrTime)) return null;
                        if ((dr.startDate == null) || (dr.startDate.getTime() < mrTime)) return new DateRange(new Timestamp(mrTime), dr.endDate, true);
                        return dr;
                    });

                if (dateRanges == null) return null;
            }
        }

        if (dateRanges.size() > 1) {
            dateRanges.sort(reverseOrder ?
                    (r1, r2) -> Long.compare(r2.endDate.getTime(), r1.endDate.getTime()) :
                    Comparator.comparingLong(r -> r.endDate.getTime()));
        }

        int remainingDateRanges = dateRanges.size();
        ArrayList<List<I>> results = new ArrayList<>(remainingDateRanges);

        Comparator<? super I> comparator = Comparator.comparingLong((I item) -> item.ts().getTime()).thenComparingInt(Item::id);
        if (reverseOrder) comparator = comparator.reversed();
        int totalSize = 0;
        I lastItem = null;

        for (DateRange dateRange : dateRanges) {
            List<I> res = selector.apply(dateRange);
            remainingDateRanges--;
            if (res.isEmpty()) continue;

            if (dateRange.edge) {
                long markerTs = markerTime;
                int mid = markerId;
                if (reverseOrder) res.removeIf(n -> (n.ts().getTime() == markerTs) && (n.id() >= mid) || (n.ts().getTime() > markerTs));
                else res.removeIf(n -> (n.ts().getTime() == markerTs) && (n.id() <= mid));

                if (res.isEmpty()) continue;
            }

            int size = res.size();
            if (size > 1) res.sort(comparator);
            totalSize += size;
            int rest = limit - totalSize;

            if (rest <= 0) {
                if (rest == 0) {
                    results.add(res);
                    if (remainingDateRanges > 0) lastItem = res.get(size - 1);
                } else {
                    int resLimit = rest + size;
                    results.add(res.subList(0, resLimit));
                    lastItem = res.get(resLimit - 1);
                }
                break;
            }

            results.add(res);
        }

        if (totalSize < limit) return new ResultList<>(results, totalSize, null);

        String nextMarker = lastItem != null ? lastItem.asString() : null;
        return new ResultList<>(results, limit, nextMarker);
    }

    public <T> List<T> toList(final Function<I,T> mapper) {
        if ((results == null) || results.isEmpty()) return null;

        ArrayList<T> res = new ArrayList<>(size);
        for (List<I> r : results) {
            for (I v : r) {
                T t;
                if ((v != null) && ((t = mapper.apply(v)) != null)) res.add(t);
            }
        }

        return res.isEmpty() ? null : res;
    }
}
