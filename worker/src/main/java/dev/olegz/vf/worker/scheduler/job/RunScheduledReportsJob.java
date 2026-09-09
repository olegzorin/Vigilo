package dev.olegz.vf.worker.scheduler.job;

import dev.olegz.vf.common.util.DateFormatUtils;
import dev.olegz.vf.report.service.ReportsService;
import dev.olegz.vf.worker.scheduler.CronJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RunScheduledReportsJob implements CronJob {
    private static final Logger logger = LoggerFactory.getLogger(RunScheduledReportsJob.class);

    private final ReportsService reportsService;

    public RunScheduledReportsJob(ReportsService reportsService) {
        this.reportsService = reportsService;
    }

    @Override
    public String name() {
        return "RunScheduledReports";
    }

    @Override
    public String cron() {
        return "0 0 8 * * ?";
    }

    @Override
    public String timeZoneId() {
        return DateFormatUtils.DEFAULT_TIMEZONE_ID;
    }

    @Override
    public void run() {
        try {
            int count = reportsService.runScheduledReports();
            logger.info("Ran {} scheduled reports", count);
        } catch (Exception e) {
            logger.error("Exception running scheduled reports", e);
        }
    }
}
