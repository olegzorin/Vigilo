package dev.olegz.vf.worker.scheduler.job;

import dev.olegz.vf.worker.scheduler.CronJob;
import dev.olegz.vf.worker.service.LambdaAsyncSubmissionOutboxDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DispatchLambdaAsyncSubmissionsJob implements CronJob {
    private static final Logger logger = LoggerFactory.getLogger(DispatchLambdaAsyncSubmissionsJob.class);

    private final LambdaAsyncSubmissionOutboxDispatcher dispatcher;

    public DispatchLambdaAsyncSubmissionsJob(LambdaAsyncSubmissionOutboxDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public String name() {
        return "DispatchLambdaAsyncSubmissions";
    }

    @Override
    public String cron() {
        return "* * * * * ?";
    }

    @Override
    public void run() {
        try {
            int dispatched = dispatcher.dispatchDueSubmissions();
            if (dispatched > 0) {
                logger.debug("Dispatched {} due asynchronous Lambda submissions", dispatched);
            }
        } catch (Exception e) {
            logger.error("Exception dispatching asynchronous Lambda submissions", e);
        }
    }
}
