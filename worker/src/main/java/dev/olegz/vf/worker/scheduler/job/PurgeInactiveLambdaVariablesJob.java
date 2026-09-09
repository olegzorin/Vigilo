package dev.olegz.vf.worker.scheduler.job;

import dev.olegz.vf.core.service.lambda.LambdaAssignmentService;
import dev.olegz.vf.worker.scheduler.CronJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author Oleg Zorin
 */
@Component
public class PurgeInactiveLambdaVariablesJob implements CronJob {
    private static final Logger logger  = LoggerFactory.getLogger(PurgeInactiveLambdaVariablesJob.class);

    private final LambdaAssignmentService lambdaAssignmentService;

    public PurgeInactiveLambdaVariablesJob(LambdaAssignmentService lambdaAssignmentService) {
        this.lambdaAssignmentService = lambdaAssignmentService;
    }

    @Override
    public String name() {
        return "PurgeLambdaVariables";
    }

    @Override
    public String cron() {
        return "0 0 0 ? * SUN";
    }

    @Override
    public void run() {
        logger.debug(">execute()");
        try {
            // TODO
//            lambdaAssignmentService.deleteInactiveVariables();
            logger.debug("<execute()");
        } catch (Exception e) {
            logger.error("Exception in PurgeLambdaVariables job", e);
        }
    }
}
