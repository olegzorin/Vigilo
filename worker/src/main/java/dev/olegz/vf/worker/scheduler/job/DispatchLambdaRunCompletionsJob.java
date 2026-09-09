package dev.olegz.vf.worker.scheduler.job;

import dev.olegz.vf.worker.scheduler.CronJob;
import dev.olegz.vf.worker.service.LambdaRunCompletionOutboxDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DispatchLambdaRunCompletionsJob implements CronJob {
    private static final Logger logger = LoggerFactory.getLogger(DispatchLambdaRunCompletionsJob.class);

    private final LambdaRunCompletionOutboxDispatcher dispatcher;

    public DispatchLambdaRunCompletionsJob(LambdaRunCompletionOutboxDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public String name() {
        return "DispatchLambdaRunCompletions";
    }

    @Override
    public String cron() {
        return "* * * * * ?";
    }

    @Override
    public void run() {
        try {
            int dispatched = dispatcher.dispatchAvailableCompletions();
            if (dispatched > 0) {
                logger.debug("Dispatched {} lambda-run completions", dispatched);
            }
        } catch (Exception e) {
            logger.error("Exception dispatching lambda-run completions", e);
        }
    }
}
