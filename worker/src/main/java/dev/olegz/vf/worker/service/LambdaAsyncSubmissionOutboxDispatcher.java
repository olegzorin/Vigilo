package dev.olegz.vf.worker.service;

import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.common.props.IntProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.domain.lambdarun.LambdaAsyncSubmissionOutboxEntry;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import dev.olegz.vf.core.service.lambda.LambdaAsyncSubmissionOutboxService;
import dev.olegz.vf.worker.domain.LambdaInvokeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LambdaAsyncSubmissionOutboxDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(LambdaAsyncSubmissionOutboxDispatcher.class);

    private final LambdaAsyncSubmissionOutboxService outboxService;
    private final LambdaRunService lambdaRunService;

    public LambdaAsyncSubmissionOutboxDispatcher(
        LambdaAsyncSubmissionOutboxService outboxService,
        LambdaRunService lambdaRunService)
    {
        this.outboxService = outboxService;
        this.lambdaRunService = lambdaRunService;
    }

    public int dispatchDueSubmissions() {
        int maxBatchSize = PropertyStore.getInt(IntProp.LAMBDA_ASYNC_SUBMISSION_DISPATCH_BATCH_SIZE);
        int dispatched = 0;

        while (dispatched < maxBatchSize) {
            LambdaAsyncSubmissionOutboxEntry entry = outboxService.claimNextDue();
            if (entry == null) break;

            dispatched++;
            try {
                LambdaInvokeRequest request = BytesMapper.readValue(entry.payload, LambdaInvokeRequest.class);
                if (request.lane != InvocationLane.ASYNC) {
                    complete(entry);
                    logger.error("Discarded non-ASYNC request from asynchronous submission outbox: {}",
                        entryDescription(entry));
                    continue;
                }

                lambdaRunService.sendAsyncRequest(request);
                complete(entry);
            } catch (Exception e) {
                try {
                    if (!outboxService.rescheduleClaim(entry, e)) {
                        logger.warn("Asynchronous submission claim changed before rescheduling: {}",
                            entryDescription(entry));
                    }
                } catch (Exception rescheduleFailure) {
                    e.addSuppressed(rescheduleFailure);
                }
                logger.error("Exception dispatching asynchronous Lambda submission: " +
                    entryDescription(entry), e);
            }
        }

        return dispatched;
    }

    private void complete(LambdaAsyncSubmissionOutboxEntry entry) {
        if (!outboxService.completeClaim(entry)) {
            throw new IllegalStateException("Asynchronous submission claim changed before completion: " +
                entryDescription(entry));
        }
    }

    private static String entryDescription(LambdaAsyncSubmissionOutboxEntry entry) {
        return "lambdaAssignmentId=" + entry.lambdaAssignmentId +
            ", runId=" + entry.runId +
            ", attemptCount=" + entry.attemptCount;
    }
}
