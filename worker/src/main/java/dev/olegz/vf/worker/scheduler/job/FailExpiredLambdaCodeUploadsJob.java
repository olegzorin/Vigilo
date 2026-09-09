package dev.olegz.vf.worker.scheduler.job;

import dev.olegz.vf.worker.scheduler.CronJob;
import dev.olegz.vf.worker.service.LambdaDeploymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FailExpiredLambdaCodeUploadsJob implements CronJob {
    private static final Logger logger = LoggerFactory.getLogger(FailExpiredLambdaCodeUploadsJob.class);

    private final LambdaDeploymentService lambdaDeploymentService;

    public FailExpiredLambdaCodeUploadsJob(LambdaDeploymentService lambdaDeploymentService) {
        this.lambdaDeploymentService = lambdaDeploymentService;
    }

    @Override
    public String name() {
        return "FailExpiredLambdaCodeUploads";
    }

    @Override
    public String cron() {
        return "0/30 * * * * ?";
    }

    @Override
    public void run() {
        try {
            int failed = lambdaDeploymentService.failExpiredCodeUploads();
            if (failed > 0) {
                logger.info("Failed {} expired lambda code uploads", failed);
            }
        } catch (Exception e) {
            logger.error("Exception failing expired lambda code uploads", e);
        }
    }
}
