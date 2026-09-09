package dev.olegz.vf.worker.scheduler.job;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import dev.olegz.vf.report.service.ReportsService;
import org.junit.jupiter.api.Test;

class RunScheduledReportsJobTest {
    @Test
    void runsReportsDailyAtEightUtc() {
        AtomicInteger calls = new AtomicInteger();
        ReportsService reportsService = (ReportsService) Proxy.newProxyInstance(
            ReportsService.class.getClassLoader(), new Class<?>[] {ReportsService.class}, (proxy, method, args) -> {
                if (method.getName().equals("runScheduledReports")) {
                    calls.incrementAndGet();
                    return 2;
                }
                throw new UnsupportedOperationException(method.getName());
            });
        RunScheduledReportsJob job = new RunScheduledReportsJob(reportsService);

        job.run();

        assertEquals("RunScheduledReports", job.name());
        assertEquals("0 0 8 * * ?", job.cron());
        assertEquals("UTC", job.timeZoneId());
        assertEquals(1, calls.get());
    }
}
