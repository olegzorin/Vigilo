package dev.olegz.vf.worker.scheduler.job;

import java.sql.Timestamp;

import dev.olegz.vf.common.props.DurationProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.service.lambda.LambdaEventReceiptService;
import dev.olegz.vf.worker.scheduler.CronJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CleanupLambdaEventReceiptsJob implements CronJob {
    private static final Logger logger = LoggerFactory.getLogger(CleanupLambdaEventReceiptsJob.class);
    private static final int BATCH_SIZE = 100;

    private final LambdaEventReceiptService receiptService;

    public CleanupLambdaEventReceiptsJob(LambdaEventReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @Override
    public String name() {
        return "CleanupLambdaEventReceipts";
    }

    @Override
    public String cron() {
        return "0 0 * * * ?";
    }

    @Override
    public void run() {
        long retention = PropertyStore.getDuration(DurationProp.LAMBDA_EVENT_RECEIPT_RETENTION).toMillis();
        Timestamp cutoff = new Timestamp(System.currentTimeMillis() - retention);
        int total = 0;
        try {
            int count;
            do {
                count = receiptService.deleteCompletedBefore(cutoff, BATCH_SIZE);
                total += count;
            } while (count == BATCH_SIZE);
            if (total > 0) logger.info("Cleaned receipts for {} lambda events", total);
        } catch (Exception e) {
            logger.error("Exception cleaning lambda event receipts after {} events", total, e);
        }
    }
}
