package dev.olegz.vf.worker.scheduler.job;

import dev.olegz.vf.core.service.lambda.LambdaClientService;
import dev.olegz.vf.worker.scheduler.CronJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DispatchExpiredLambdaRunCompletionsJob implements CronJob {
    private static final Logger logger = LoggerFactory.getLogger(DispatchExpiredLambdaRunCompletionsJob.class);

    private final LambdaClientService lambdaClientService;

    public DispatchExpiredLambdaRunCompletionsJob(LambdaClientService lambdaClientService) {
        this.lambdaClientService = lambdaClientService;
    }

    @Override
    public String name() {
        return "DispatchExpiredLambdaRunCompletions";
    }

    @Override
    public String cron() {
        return "* * * * * ?";
    }

    @Override
    public void run() {
        try {
            lambdaClientService.dispatchExpiredLambdaRunCompletions();
        } catch (Exception e) {
            logger.error("Exception in running DispatchExpiredLambdaRunCompletions job", e);
        }
    }
}
