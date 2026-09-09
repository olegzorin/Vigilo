package dev.olegz.vf.worker.scheduler.job;

import dev.olegz.vf.worker.scheduler.CronJob;
import dev.olegz.vf.worker.service.LambdaDeploymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CleanupObsoleteLambdaDeploymentsJob implements CronJob {
    private static final Logger logger = LoggerFactory.getLogger(CleanupObsoleteLambdaDeploymentsJob.class);

    private final LambdaDeploymentService lambdaDeploymentService;

    public CleanupObsoleteLambdaDeploymentsJob(LambdaDeploymentService lambdaDeploymentService) {
        this.lambdaDeploymentService = lambdaDeploymentService;
    }

    @Override
    public String name() {
        return "PurgeLambdaDeployments";
    }

    @Override
    public String cron() {
        return "0 0 0 ? * SUN";
    }

    @Override
    public void run() {
        try {
            lambdaDeploymentService.purgeDeployments();
        } catch (Exception e) {
            logger.error("Exception in PurgeLambdaDeployments job", e);
        }
    }
}
