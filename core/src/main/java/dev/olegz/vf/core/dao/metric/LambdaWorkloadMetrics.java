package dev.olegz.vf.core.dao.metric;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import dev.olegz.vf.common.monitor.TimeSlice;
import dev.olegz.vf.common.monitor.WorkloadMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LambdaWorkloadMetrics {

    private static final Logger logger = LoggerFactory.getLogger(LambdaWorkloadMetrics.class);

    private static final int threeDaysMinutes = 3 * 24 * 60;
    private static final long threeDaysMillis = 3 * 24 * 3600_000;

    private static final Map<LambdaWorkloadMetric.Operation, MetricMonitor> monitors =
        Collections.synchronizedMap(new EnumMap<>(LambdaWorkloadMetric.Operation.class));

    private LambdaWorkloadMetrics() {
    }

    record Metrics(double avgCalls, int maxCalls, long avgCallTime, int totalCalls) {
    }

    private static class MetricMonitor {
        private final WorkloadMonitor calls;
        private final WorkloadMonitor failures;

        private MetricMonitor(String name) {
            calls = new WorkloadMonitor(name, threeDaysMillis, threeDaysMinutes, 50);
            failures = new WorkloadMonitor(name + ".failures", threeDaysMillis, threeDaysMinutes, 50);
        }

        private void add(long durationMicros, boolean success) {
            calls.addTaskDuration(durationMicros);
            if (!success) failures.addTaskDuration(durationMicros);
        }
    }

    /** Returns workload snapshots in the stable order defined by {@link LambdaWorkloadMetric.Operation}. */
    public static List<LambdaWorkloadMetricSnapshot> snapshot() {
        if (monitors.isEmpty()) return List.of();

        ArrayList<LambdaWorkloadMetricSnapshot> metrics = new ArrayList<>(monitors.size());

        monitors.forEach((operation, monitor) -> {
            try {
                TimeSlice[] timeSlices = monitor.calls.getTimeSeries();
                TimeSlice[] failureSlices = monitor.failures.getTimeSeries();
                Metrics metric1Hour = getMetrics(subArray(timeSlices, 60));
                Metrics metric6Hours = getMetrics(subArray(timeSlices, 360));
                Metrics metric3Days = getMetrics(timeSlices);
                metrics.add(new LambdaWorkloadMetricSnapshot(operation.metricName(),
                    printElapsedTime(monitor.calls.getElapsedTime()),
                    metric1Hour.avgCalls(), metric1Hour.maxCalls(), metric1Hour.avgCallTime(), metric1Hour.totalCalls(),
                    getTotalCalls(subArray(failureSlices, 60)),
                    metric6Hours.avgCalls(), metric6Hours.maxCalls(), metric6Hours.avgCallTime(), metric6Hours.totalCalls(),
                    getTotalCalls(subArray(failureSlices, 360)),
                    metric3Days.avgCalls(), metric3Days.maxCalls(), metric3Days.avgCallTime(), metric3Days.totalCalls(),
                    getTotalCalls(failureSlices))
                );
            } catch (Exception e) {
                logger.error("Exception in getMetrics for " + operation, e);
            }
        });
        return metrics;
    }

    private static TimeSlice[] subArray(TimeSlice[] timeSlices, int lastN) {
        int len = timeSlices.length;
        return lastN >= len ? timeSlices : Arrays.copyOfRange(timeSlices, len - lastN, len);
    }

    static Metrics getMetrics(TimeSlice[] timeSlices) {
        if ((timeSlices == null) || (timeSlices.length == 0)) {
            return new Metrics(0.0, 0, 0L, 0);
        }

        int totCalls = 0;
        int maxCalls = 0;
        long totDuration = 0L;
        for (TimeSlice timeSlice : timeSlices) {
            totCalls += timeSlice.count();
            maxCalls = Math.max(timeSlice.count(), maxCalls);
            totDuration += timeSlice.duration();
        }
        if (totCalls == 0) {
            return new Metrics(0.0, 0, 0L, 0);
        }

        double avgCalls = (double) totCalls / timeSlices.length;
        long avgCallTime = totDuration / totCalls;
        return new Metrics(avgCalls, maxCalls, avgCallTime, totCalls);
    }

    private static int getTotalCalls(TimeSlice[] timeSlices) {
        return getMetrics(timeSlices).totalCalls();
    }

    static void addCallTimeNanos(LambdaWorkloadMetric.Operation operation, long startTimeNanos, long endTimeNanos,
                                 boolean success) {
        long durationMicros = (endTimeNanos - startTimeNanos) / 1000L;
        monitors.computeIfAbsent(operation, n -> new MetricMonitor(n.metricName())).add(durationMicros, success);
    }

    private static String printElapsedTime(long millis) {
        int minutes = (int) (millis / 60_000L);
        int hours = minutes / 60;
        int days = hours / 24;
        return minutes < 60 ? minutes + "m" :
            hours < 24 ? hours + "h" + (minutes % 60) + "m" :
                days + "d" + (hours % 24) + "h" + (minutes % 60) + "m";
    }
}
