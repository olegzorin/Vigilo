package dev.olegz.vf.worker.scheduler.job;

import dev.olegz.vf.worker.scheduler.CronJob;
import dev.olegz.vf.worker.service.LambdaInvokeRetryOutboxDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DispatchLambdaInvokeRetriesJob implements CronJob {
    private static final Logger logger = LoggerFactory.getLogger(DispatchLambdaInvokeRetriesJob.class);

    private final LambdaInvokeRetryOutboxDispatcher dispatcher;

    public DispatchLambdaInvokeRetriesJob(LambdaInvokeRetryOutboxDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public String name() {
        return "DispatchLambdaInvokeRetries";
    }

    @Override
    public String cron() {
        return "* * * * * ?";
    }

    @Override
    public void run() {
        try {
            int dispatched = dispatcher.dispatchDueRetries();
            if (dispatched > 0) {
                logger.debug("Dispatched {} due lambda invocation retries", dispatched);
            }
        } catch (Exception e) {
            logger.error("Exception dispatching lambda invocation retries", e);
        }
    }
}
