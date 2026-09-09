package dev.olegz.vf.worker.scheduler.job;

import dev.olegz.vf.core.service.lambda.LambdaAssignmentCleanupService;
import dev.olegz.vf.worker.scheduler.CronJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CleanupCancelledLambdaAssignmentsJob implements CronJob {
    private static final Logger logger = LoggerFactory.getLogger(CleanupCancelledLambdaAssignmentsJob.class);

    private final LambdaAssignmentCleanupService cleanupService;

    public CleanupCancelledLambdaAssignmentsJob(LambdaAssignmentCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Override
    public String name() {
        return "CleanupCancelledLambdaAssignments";
    }

    @Override
    public String cron() {
        return "0 0 2 * * ?";
    }

    @Override
    public void run() {
        int count = 0;
        try {
            while (cleanupService.cleanupNextLambdaAssignment() != null) {
                count++;
            }
            logger.info("Cleaned runtime data for {} cancelled lambda assignments", count);
        } catch (Exception e) {
            logger.error("Exception cleaning cancelled lambda assignment data after {} assignments", count, e);
        }
    }
}
