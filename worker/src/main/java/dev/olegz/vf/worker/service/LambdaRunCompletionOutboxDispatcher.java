package dev.olegz.vf.worker.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.common.props.DurationProp;
import dev.olegz.vf.common.props.IntProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.domain.lambdarun.LambdaRunCompletionOutboxEntry;
import dev.olegz.vf.core.domain.lambdarun.LambdaRunContext;
import dev.olegz.vf.core.service.lambda.LambdaRunCompletionOutboxService;
import dev.olegz.vf.messaging.ConfirmingMessageProducer;
import dev.olegz.vf.messaging.Messaging;
import dev.olegz.vf.messaging.MessagingProvider;
import dev.olegz.vf.messaging.Topics;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LambdaRunCompletionOutboxDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(LambdaRunCompletionOutboxDispatcher.class);

    private final LambdaRunCompletionOutboxService outboxService;
    private final ConfirmingMessageProducer producer;
    private final ScheduledExecutorService leaseRenewalExecutor;

    @Autowired
    public LambdaRunCompletionOutboxDispatcher(LambdaRunCompletionOutboxService outboxService) {
        this(
            outboxService,
            Messaging.producer(MessagingProvider.KAFKA, "lambdaRunCompletion", ConfirmingMessageProducer.class),
            Executors.newSingleThreadScheduledExecutor());
    }

    LambdaRunCompletionOutboxDispatcher(
        LambdaRunCompletionOutboxService outboxService,
        ConfirmingMessageProducer producer,
        ScheduledExecutorService leaseRenewalExecutor)
    {
        this.outboxService = outboxService;
        this.producer = producer;
        this.leaseRenewalExecutor = leaseRenewalExecutor;
    }

    public int dispatchAvailableCompletions() {
        int maxBatchSize = PropertyStore.getInt(IntProp.LAMBDA_COMPLETION_OUTBOX_DISPATCH_BATCH_SIZE);
        int dispatched = 0;

        while (dispatched < maxBatchSize) {
            LambdaRunCompletionOutboxEntry entry = outboxService.claimNextAvailable();
            if (entry == null) break;

            ScheduledFuture<?> leaseRenewal = startLeaseRenewal(entry);
            try {
                producer.sendAndAwait(Topics.LAMBDA_RUN_COMPLETION, completionPayload(entry));
                if (!outboxService.completeClaim(entry)) {
                    throw new IllegalStateException("Completion claim changed after Kafka publication: " +
                        entryDescription(entry));
                }
                dispatched++;
            } catch (Exception e) {
                releaseClaim(entry, e);
                logger.error("Exception publishing claimed lambda-run completion: " + entryDescription(entry), e);
                break;
            } finally {
                leaseRenewal.cancel(false);
            }
        }

        return dispatched;
    }

    private static byte[] completionPayload(LambdaRunCompletionOutboxEntry entry) {
        LambdaRunContext context = new LambdaRunContext();
        context.lambdaAssignmentId = entry.lambdaAssignmentId;
        context.lane = entry.lane;
        context.requestId = entry.runId;
        return BytesMapper.writeValue(context);
    }

    private ScheduledFuture<?> startLeaseRenewal(LambdaRunCompletionOutboxEntry entry) {
        long leaseMillis =
            PropertyStore.getDuration(DurationProp.LAMBDA_COMPLETION_OUTBOX_CLAIM_LEASE).toMillis();
        long renewalPeriodMillis = Math.max(100L, leaseMillis / 2);
        return leaseRenewalExecutor.scheduleAtFixedRate(
            () -> renewClaim(entry),
            renewalPeriodMillis,
            renewalPeriodMillis,
            TimeUnit.MILLISECONDS);
    }

    private void renewClaim(LambdaRunCompletionOutboxEntry entry) {
        try {
            if (!outboxService.renewClaim(entry)) {
                logger.warn("Completion claim could not be renewed: {}", entryDescription(entry));
            }
        } catch (Exception e) {
            logger.error("Exception renewing completion claim: " + entryDescription(entry), e);
        }
    }

    private void releaseClaim(LambdaRunCompletionOutboxEntry entry, Throwable cause) {
        try {
            outboxService.releaseClaim(entry);
        } catch (Exception releaseError) {
            cause.addSuppressed(releaseError);
        }
    }

    private static String entryDescription(LambdaRunCompletionOutboxEntry entry) {
        return "lambdaAssignmentId=" + entry.lambdaAssignmentId +
            ", lane=" + entry.lane +
            ", runId=" + entry.runId;
    }

    @PreDestroy
    public void shutdown() {
        leaseRenewalExecutor.shutdownNow();
    }
}
