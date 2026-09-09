package dev.olegz.vf.core.dao.metric;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import dev.olegz.vf.common.monitor.TimeSlice;
import org.junit.jupiter.api.Test;

import static dev.olegz.vf.core.dao.metric.LambdaWorkloadMetric.Operation.sqlInsertLambdaRunsInfo;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LambdaWorkloadMetricsTest {

    @Test
    void summarizesCompletedMinuteSlicesWithoutPretendingToCalculatePercentiles() {
        TimeSlice[] slices = {
            new TimeSlice(0L, 60_000L, 1, 10L),
            new TimeSlice(60_000L, 120_000L, 2, 50L)
        };

        LambdaWorkloadMetrics.Metrics metrics = LambdaWorkloadMetrics.getMetrics(slices);

        assertEquals(1.5, metrics.avgCalls());
        assertEquals(2, metrics.maxCalls());
        assertEquals(20L, metrics.avgCallTime());
        assertEquals(3, metrics.totalCalls());
    }

    @Test
    void concurrentFirstObservationsCreateOneMetricSnapshot() throws InterruptedException {
        int threadCount = 32;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = Thread.ofPlatform().start(() -> {
                ready.countDown();
                try {
                    start.await();
                    LambdaWorkloadMetrics.addCallTimeNanos(sqlInsertLambdaRunsInfo, 0L, 1_000L, true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        start.countDown();
        for (Thread thread : threads) thread.join();

        List<LambdaWorkloadMetricSnapshot> snapshots = LambdaWorkloadMetrics.snapshot();
        assertEquals(1L, snapshots.stream().filter(m -> m.name().equals("sql.insertLambdaRunsInfo")).count());
    }
}
