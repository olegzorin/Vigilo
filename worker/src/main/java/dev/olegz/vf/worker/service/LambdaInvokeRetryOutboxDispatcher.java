package dev.olegz.vf.worker.service;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.common.props.DurationProp;
import dev.olegz.vf.common.props.IntProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.domain.lambdarun.LambdaInvokeRetryOutboxEntry;
import dev.olegz.vf.core.service.lambda.LambdaInvokeRetryOutboxService;
import dev.olegz.vf.worker.domain.LambdaInvocationOutcome;
import dev.olegz.vf.worker.domain.LambdaInvokeRequest;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LambdaInvokeRetryOutboxDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(LambdaInvokeRetryOutboxDispatcher.class);

    private final LambdaInvokeRetryOutboxService outboxService;
    private final LambdaRunService lambdaRunService;
    private final ScheduledExecutorService leaseRenewalExecutor;

    @Autowired
    public LambdaInvokeRetryOutboxDispatcher(
        LambdaInvokeRetryOutboxService outboxService,
        LambdaRunService lambdaRunService)
    {
        this(outboxService, lambdaRunService, Executors.newSingleThreadScheduledExecutor());
    }

    LambdaInvokeRetryOutboxDispatcher(
        LambdaInvokeRetryOutboxService outboxService,
        LambdaRunService lambdaRunService,
        ScheduledExecutorService leaseRenewalExecutor)
    {
        this.outboxService = outboxService;
        this.lambdaRunService = lambdaRunService;
        this.leaseRenewalExecutor = leaseRenewalExecutor;
    }

    public int dispatchDueRetries() {
        int maxBatchSize = PropertyStore.getInt(IntProp.LAMBDA_RETRY_OUTBOX_DISPATCH_BATCH_SIZE);
        int dispatched = 0;

        while (dispatched < maxBatchSize) {
            LambdaInvokeRetryOutboxEntry entry = outboxService.claimNextDue();
            if (entry == null) break;

            ScheduledFuture<?> leaseRenewal = null;
            try {
                LambdaInvokeRequest request = BytesMapper.readValue(entry.payload, LambdaInvokeRequest.class);
                leaseRenewal = startLeaseRenewal(entry);
                ScheduledFuture<?> finalLeaseRenewal = leaseRenewal;
                lambdaRunService.executeDefaultLaneLambdaRequest(request).whenComplete(
                    (outcome, error) -> completeAttempt(entry, finalLeaseRenewal, outcome, error));
                dispatched++;
            } catch (RejectedExecutionException e) {
                cancel(leaseRenewal);
                releaseClaim(entry, e);
                break;
            } catch (Exception e) {
                cancel(leaseRenewal);
                releaseClaim(entry, e);
                throw e;
            }
        }

        return dispatched;
    }

    private ScheduledFuture<?> startLeaseRenewal(LambdaInvokeRetryOutboxEntry entry) {
        long leaseMillis =
            PropertyStore.getDuration(DurationProp.LAMBDA_RETRY_OUTBOX_CLAIM_LEASE).toMillis();
        long renewalPeriodMillis = Math.max(100L, leaseMillis / 2);
        return leaseRenewalExecutor.scheduleAtFixedRate(
            () -> renewClaim(entry),
            renewalPeriodMillis,
            renewalPeriodMillis,
            TimeUnit.MILLISECONDS);
    }

    private void renewClaim(LambdaInvokeRetryOutboxEntry entry) {
        try {
            if (!outboxService.renewClaim(entry)) {
                logger.warn("Retry claim could not be renewed: {}", entryDescription(entry));
            }
        } catch (Exception e) {
            logger.error("Exception renewing retry claim: " + entryDescription(entry), e);
        }
    }

    private void completeAttempt(
        LambdaInvokeRetryOutboxEntry entry,
        ScheduledFuture<?> leaseRenewal,
        LambdaInvocationOutcome outcome,
        Throwable error)
    {
        cancel(leaseRenewal);
        if (error != null) {
            releaseClaim(entry, error);
            logger.error("Exception executing claimed lambda invocation retry: " + entryDescription(entry), error);
            return;
        }

        AtomicBoolean completed = new AtomicBoolean();
        try {
            lambdaRunService.processDefaultLaneInvocationOutcome(outcome, () -> {
                if (!outboxService.completeClaim(entry)) {
                    throw new IllegalStateException("Retry claim changed before completion: " +
                        entryDescription(entry));
                }
                completed.set(true);
            });
        } catch (Exception e) {
            if (!completed.get()) releaseClaim(entry, e);
            logger.error("Exception completing claimed lambda invocation retry: " + entryDescription(entry), e);
        }
    }

    private void releaseClaim(LambdaInvokeRetryOutboxEntry entry, Throwable cause) {
        try {
            outboxService.releaseClaim(entry);
        } catch (Exception releaseError) {
            cause.addSuppressed(releaseError);
        }
    }

    private static void cancel(ScheduledFuture<?> task) {
        if (task != null) task.cancel(false);
    }

    private static String entryDescription(LambdaInvokeRetryOutboxEntry entry) {
        return "lambdaAssignmentId=" + entry.lambdaAssignmentId +
            ", lane=" + entry.lane +
            ", runId=" + entry.runId +
            ", invocationGen=" + entry.invocationGen;
    }

    @PreDestroy
    public void shutdown() {
        leaseRenewalExecutor.shutdownNow();
    }
}
