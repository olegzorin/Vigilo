package dev.olegz.vf.worker.scheduler.job;

import dev.olegz.vf.worker.scheduler.CronJob;
import dev.olegz.vf.worker.service.LambdaResetOutboxDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DispatchLambdaResetsJob implements CronJob {
    private static final Logger logger = LoggerFactory.getLogger(DispatchLambdaResetsJob.class);

    private final LambdaResetOutboxDispatcher dispatcher;

    public DispatchLambdaResetsJob(LambdaResetOutboxDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public String name() {
        return "DispatchLambdaResets";
    }

    @Override
    public String cron() {
        return "* * * * * ?";
    }

    @Override
    public void run() {
        try {
            int dispatched = dispatcher.dispatchPending();
            if (dispatched > 0) logger.debug("Dispatched {} lambda resets", dispatched);
        } catch (Exception e) {
            logger.error("Exception dispatching lambda resets", e);
        }
    }
}
