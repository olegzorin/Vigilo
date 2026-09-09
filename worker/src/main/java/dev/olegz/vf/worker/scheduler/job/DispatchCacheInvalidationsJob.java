package dev.olegz.vf.worker.scheduler.job;

import dev.olegz.vf.worker.scheduler.CronJob;
import dev.olegz.vf.worker.service.CacheInvalidationOutboxDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DispatchCacheInvalidationsJob implements CronJob {
    private static final Logger logger = LoggerFactory.getLogger(DispatchCacheInvalidationsJob.class);

    private final CacheInvalidationOutboxDispatcher dispatcher;

    public DispatchCacheInvalidationsJob(CacheInvalidationOutboxDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public String name() {
        return "DispatchCacheInvalidations";
    }

    @Override
    public String cron() {
        return "* * * * * ?";
    }

    @Override
    public void run() {
        try {
            int dispatched = dispatcher.dispatchPending();
            if (dispatched > 0) {
                logger.debug("Dispatched {} cache invalidations", dispatched);
            }
        } catch (Exception e) {
            logger.error("Exception dispatching cache invalidations", e);
        }
    }
}
