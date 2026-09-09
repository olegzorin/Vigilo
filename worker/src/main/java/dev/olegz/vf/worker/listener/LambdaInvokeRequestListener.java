package dev.olegz.vf.worker.listener;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.common.util.DateFormatUtils;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import dev.olegz.vf.core.service.lambda.LambdaAsyncSubmissionOutboxService;
import dev.olegz.vf.messaging.MessageListener;
import dev.olegz.vf.messaging.consumer.AckStatus;
import dev.olegz.vf.worker.domain.LambdaInvokeRequest;
import dev.olegz.vf.worker.service.LambdaRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LambdaInvokeRequestListener implements MessageListener {
    private static final Logger logger = LoggerFactory.getLogger(LambdaInvokeRequestListener.class);

    private static final long REJECTED_REQUEST_DELAY = PropertyStore.getLong("vf.lambda.invoke.rejectedDelay", 100L);
    private static final int REJECTED_REQUEST_REPEAT = PropertyStore.getInt("vf.lambda.invoke.rejectedRepeat", 30);
    private static final int REJECTED_REQUEST_LOG_COUNT = PropertyStore.getInt("vf.lambda.invoke.rejectedLog", 1000);

    private final LambdaRunService lambdaRunService;
    private final LambdaAsyncSubmissionOutboxService asyncSubmissionOutboxService;

    private final AtomicInteger rejectCounter = new AtomicInteger();
    private volatile long rejectLogTime = System.currentTimeMillis();

    public LambdaInvokeRequestListener(
        LambdaRunService lambdaRunService,
        LambdaAsyncSubmissionOutboxService asyncSubmissionOutboxService)
    {
        this.lambdaRunService = lambdaRunService;
        this.asyncSubmissionOutboxService = asyncSubmissionOutboxService;
    }

    @Override
    public void onMessage(byte[] messageBytes, AckStatus ackStatus) {
        final LambdaInvokeRequest request;
        try {
            request = BytesMapper.readValue(messageBytes, LambdaInvokeRequest.class);
        } catch (Exception e) {
            ackStatus.acknowledge();
            logger.error("Cannot parse AWS Lambda request", e);
            return;
        }

        if (request.appInput == null || request.appInput.isBlank()) {
            ackStatus.acknowledge();
            logger.error("Empty input for " + request);
            return;
        }

        if (request.lane == InvocationLane.ASYNC) {
            submitAsyncRequest(messageBytes, request, ackStatus);
            return;
        }

        try {
            try {
                executeRequest(request, ackStatus);
            } catch (RejectedExecutionException e) {
                lambdaRunService.resubmitInvokeRequest(messageBytes);
                ackStatus.acknowledge();
                logRejectedRequest();
            }
        } catch (Exception e) {
            ackStatus.acknowledge();
            logger.error("Exception in processing AWS Lambda request", e);
        }
    }

    private void submitAsyncRequest(byte[] messageBytes, LambdaInvokeRequest request, AckStatus ackStatus) {
        try {
            lambdaRunService.sendAsyncRequest(request);
            ackStatus.acknowledge();
        } catch (Exception submissionFailure) {
            try {
                asyncSubmissionOutboxService.enqueue(
                    request.lambdaAssignmentId, request.requestId, messageBytes, submissionFailure);
                ackStatus.acknowledge();
                logger.warn("Deferred asynchronous Lambda submission {}", request.toLogString(), submissionFailure);
            } catch (Exception persistenceFailure) {
                submissionFailure.addSuppressed(persistenceFailure);
                logger.error("Cannot submit or durably defer asynchronous Lambda request " +
                    request.toLogString(), submissionFailure);
            }
        }
    }

    private void executeRequest(LambdaInvokeRequest request, AckStatus ackStatus) throws RejectedExecutionException {
        for (int i = REJECTED_REQUEST_REPEAT; i >= 0; i--) {
            try {
                lambdaRunService.executeDefaultLaneLambdaRequest(request).whenComplete((outcome, error) -> {
                    if (error != null) {
                        logger.error("Exception executing default-lane lambda request " + request, error);
                        return;
                    }

                    try {
                        lambdaRunService.processDefaultLaneInvocationOutcome(outcome, ackStatus::acknowledge);
                    } catch (Exception e) {
                        logger.error("Exception in consuming response\n" + outcome.request(), e);
                    }
                });
                break;
            } catch (RejectedExecutionException e) {
                if (i == 0) throw e;
                try {
                    Thread.sleep(REJECTED_REQUEST_DELAY);
                } catch (InterruptedException ie) {
                    throw e;
                }
            }
        }
    }

    private void logRejectedRequest() {
        int count = rejectCounter.incrementAndGet();

        if (count > REJECTED_REQUEST_LOG_COUNT) {
            rejectCounter.set(0);
            logger.warn(count + " rejected requests since " + DateFormatUtils.logTimestamp(rejectLogTime));
            rejectLogTime = System.currentTimeMillis();
        }
    }

    @Override
    public void onMessage(byte[] messageBytes) {
        logger.error("Wrong AWS Lambda request method called");
    }
}
