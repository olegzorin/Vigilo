package dev.olegz.vf.worker.listener;

import java.util.concurrent.CompletableFuture;

import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.domain.lambdarun.LambdaAsyncSubmissionOutboxEntry;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import dev.olegz.vf.core.service.lambda.LambdaAsyncSubmissionOutboxService;
import dev.olegz.vf.messaging.consumer.AckStatus;
import dev.olegz.vf.worker.domain.LambdaInvocationOutcome;
import dev.olegz.vf.worker.domain.LambdaInvokeRequest;
import dev.olegz.vf.worker.service.LambdaRunService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LambdaInvokeRequestListenerTest {
    private RecordingLambdaRunService lambdaRunService;
    private LambdaInvokeRequestListener listener;

    @BeforeAll
    static void oneTimeSetUp() {
        PropertyStore.start();
    }

    @Test
    void acknowledgeOnlyOutcomeAcknowledgesOriginalMessage() {
        LambdaInvokeRequest request = request(InvocationLane.DEFAULT);
        lambdaRunService = new RecordingLambdaRunService();
        lambdaRunService.outcome = CompletableFuture.completedFuture(LambdaInvocationOutcome.acknowledgeOnly(request));
        listener = new LambdaInvokeRequestListener(lambdaRunService, new RecordingAsyncOutboxService());
        AckStatus ack = new AckStatus();

        listener.onMessage(BytesMapper.writeValue(request), ack);

        assertTrue(ack.isAcknowledged());
        assertEquals(1, lambdaRunService.defaultLaneInvocations);
    }

    @Test
    void persistedRetryAcknowledgesOriginalMessageWithoutRepublishingFromListener() {
        LambdaInvokeRequest request = request(InvocationLane.DEFAULT);
        LambdaInvokeRequest retry = request.createRetryAttempt();
        lambdaRunService = new RecordingLambdaRunService();
        lambdaRunService.outcome = CompletableFuture.completedFuture(LambdaInvocationOutcome.retryPersisted(retry));
        listener = new LambdaInvokeRequestListener(lambdaRunService, new RecordingAsyncOutboxService());
        AckStatus ack = new AckStatus();

        listener.onMessage(BytesMapper.writeValue(request), ack);

        assertTrue(ack.isAcknowledged());
        assertEquals(1, lambdaRunService.defaultLaneInvocations);
    }

    @Test
    void failedAttemptCoordinationLeavesOriginalMessageUnacknowledged() {
        LambdaInvokeRequest request = request(InvocationLane.DEFAULT);
        lambdaRunService = new RecordingLambdaRunService();
        lambdaRunService.outcome = CompletableFuture.failedFuture(new RuntimeException("state unavailable"));
        listener = new LambdaInvokeRequestListener(lambdaRunService, new RecordingAsyncOutboxService());
        AckStatus ack = new AckStatus();

        listener.onMessage(BytesMapper.writeValue(request), ack);

        assertFalse(ack.isAcknowledged());
    }

    @Test
    void asyncRequestBypassesDefaultLaneInvocation() {
        LambdaInvokeRequest request = request(InvocationLane.ASYNC);
        lambdaRunService = new RecordingLambdaRunService();
        listener = new LambdaInvokeRequestListener(lambdaRunService, new RecordingAsyncOutboxService());
        AckStatus ack = new AckStatus();

        listener.onMessage(BytesMapper.writeValue(request), ack);

        assertTrue(ack.isAcknowledged());
        assertEquals(1, lambdaRunService.asyncInvocations);
        assertEquals(0, lambdaRunService.defaultLaneInvocations);
    }

    @Test
    void failedAsyncSubmissionIsAcknowledgedOnlyAfterDurableDeferral() {
        LambdaInvokeRequest request = request(InvocationLane.ASYNC);
        lambdaRunService = new RecordingLambdaRunService();
        lambdaRunService.asyncFailure = new RuntimeException("Lambda unavailable");
        RecordingAsyncOutboxService outbox = new RecordingAsyncOutboxService();
        listener = new LambdaInvokeRequestListener(lambdaRunService, outbox);
        AckStatus ack = new AckStatus();

        listener.onMessage(BytesMapper.writeValue(request), ack);

        assertTrue(ack.isAcknowledged());
        assertEquals(1, outbox.enqueued);
    }

    @Test
    void failedAsyncSubmissionRemainsUnacknowledgedWhenDurableDeferralFails() {
        LambdaInvokeRequest request = request(InvocationLane.ASYNC);
        lambdaRunService = new RecordingLambdaRunService();
        lambdaRunService.asyncFailure = new RuntimeException("Lambda unavailable");
        RecordingAsyncOutboxService outbox = new RecordingAsyncOutboxService();
        outbox.enqueueFailure = new RuntimeException("database unavailable");
        listener = new LambdaInvokeRequestListener(lambdaRunService, outbox);
        AckStatus ack = new AckStatus();

        listener.onMessage(BytesMapper.writeValue(request), ack);

        assertFalse(ack.isAcknowledged());
        assertEquals(1, outbox.enqueued);
    }

    private static LambdaInvokeRequest request(InvocationLane lane) {
        LambdaInvokeRequest request = new LambdaInvokeRequest();
        request.requestId = 1_750_000_000_000L;
        request.lambdaAssignmentId = 17;
        request.lambdaId = 4;
        request.lambdaFunction = "test-function";
        request.appInput = "{\"invocationToken\":1750000000000000}";
        request.lane = lane;
        request.timeout = 30_000;
        return request;
    }

    private static class RecordingLambdaRunService extends LambdaRunService {
        private CompletableFuture<LambdaInvocationOutcome> outcome;
        private int defaultLaneInvocations;
        private int asyncInvocations;
        private RuntimeException asyncFailure;

        RecordingLambdaRunService() {
            super(null, null, null, null);
        }

        @Override
        public CompletableFuture<LambdaInvocationOutcome> executeDefaultLaneLambdaRequest(LambdaInvokeRequest request) {
            defaultLaneInvocations++;
            return outcome;
        }

        @Override
        public boolean sendAsyncRequest(LambdaInvokeRequest request) {
            asyncInvocations++;
            if (asyncFailure != null) throw asyncFailure;
            return true;
        }
    }

    private static class RecordingAsyncOutboxService implements LambdaAsyncSubmissionOutboxService {
        private int enqueued;
        private RuntimeException enqueueFailure;

        @Override
        public void enqueue(int lambdaAssignmentId, long runId, byte[] payload, Throwable failure) {
            enqueued++;
            if (enqueueFailure != null) throw enqueueFailure;
        }

        @Override
        public LambdaAsyncSubmissionOutboxEntry claimNextDue() {
            return null;
        }

        @Override
        public boolean completeClaim(LambdaAsyncSubmissionOutboxEntry entry) {
            return false;
        }

        @Override
        public boolean rescheduleClaim(LambdaAsyncSubmissionOutboxEntry entry, Throwable failure) {
            return false;
        }
    }
}
