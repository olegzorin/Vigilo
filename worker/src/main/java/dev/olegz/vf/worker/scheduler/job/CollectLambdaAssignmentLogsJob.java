package dev.olegz.vf.worker.scheduler.job;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import dev.olegz.vf.core.domain.lambdarun.LambdaAssignmentLog;
import dev.olegz.vf.core.service.lambda.LambdaLogService;
import dev.olegz.vf.worker.scheduler.CronJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CollectLambdaAssignmentLogsJob implements CronJob {
    private static final Logger logger = LoggerFactory.getLogger(CollectLambdaAssignmentLogsJob.class);

    private final LambdaLogService lambdaLogService;

    public CollectLambdaAssignmentLogsJob(LambdaLogService lambdaLogService) {
        this.lambdaLogService = lambdaLogService;
    }

    @Override
    public String name() {
        return "CollectLambdaAssignmentLogs";
    }

    @Override
    public String cron() {
        return "0 0/3 * * * ?";
    }

    @Override
    public void run() {
        logger.debug(">execute()");
        logger.debug("<execute()");
    }

    private void writeLogs(Collection<List<LambdaAssignmentLog>> allLogs) {
        // Iterate over lambdaAssignmentId:lane
        for (List<LambdaAssignmentLog> logs : allLogs) {
            // There may be a mix of batches here:
            if (logs.size() > 1) {
                // Order by requestId and batchIndex
                Collections.sort(logs);
            }
            logs.forEach(lambdaLogService::writeLambdaAssignmentLog);
        }
    }
}
