package dev.olegz.vf.worker.service;

import java.sql.Timestamp;
import java.util.ArrayDeque;
import java.util.Queue;

import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.domain.lambdarun.LambdaAsyncSubmissionOutboxEntry;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import dev.olegz.vf.core.service.lambda.LambdaAsyncSubmissionOutboxService;
import dev.olegz.vf.worker.domain.LambdaInvokeRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LambdaAsyncSubmissionOutboxDispatcherTest {

    @BeforeAll
    static void oneTimeSetUp() {
        PropertyStore.start();
    }

    @Test
    void completesSuccessfullySubmittedAndStaleRows() {
        RecordingOutboxService outbox = new RecordingOutboxService(entry(1), entry(2));
        RecordingLambdaRunService lambdaRunService = new RecordingLambdaRunService();
        lambdaRunService.staleRunId = 2;
        LambdaAsyncSubmissionOutboxDispatcher dispatcher =
            new LambdaAsyncSubmissionOutboxDispatcher(outbox, lambdaRunService);

        int count = dispatcher.dispatchDueSubmissions();

        assertEquals(2, count);
        assertEquals(2, lambdaRunService.submissions);
        assertEquals(2, outbox.completed);
        assertEquals(0, outbox.rescheduled);
    }

    @Test
    void failedSubmissionIsRescheduledUnderItsClaim() {
        RecordingOutboxService outbox = new RecordingOutboxService(entry(1));
        RecordingLambdaRunService lambdaRunService = new RecordingLambdaRunService();
        lambdaRunService.failure = new RuntimeException("Lambda unavailable");
        LambdaAsyncSubmissionOutboxDispatcher dispatcher =
            new LambdaAsyncSubmissionOutboxDispatcher(outbox, lambdaRunService);

        int count = dispatcher.dispatchDueSubmissions();

        assertEquals(1, count);
        assertEquals(0, outbox.completed);
        assertEquals(1, outbox.rescheduled);
    }

    private static LambdaAsyncSubmissionOutboxEntry entry(long runId) {
        LambdaInvokeRequest request = new LambdaInvokeRequest();
        request.lambdaAssignmentId = 17;
        request.requestId = runId;
        request.lambdaFunction = "test-function";
        request.appInput = "{\"invocationToken\":1750000000000000}";
        request.lane = InvocationLane.ASYNC;

        LambdaAsyncSubmissionOutboxEntry entry = new LambdaAsyncSubmissionOutboxEntry(
            request.lambdaAssignmentId,
            runId,
            BytesMapper.writeValue(request),
            new Timestamp(System.currentTimeMillis()),
            "initial failure");
        entry.claimId = "claim-" + runId;
        return entry;
    }

    private static class RecordingOutboxService implements LambdaAsyncSubmissionOutboxService {
        private final Queue<LambdaAsyncSubmissionOutboxEntry> entries = new ArrayDeque<>();
        private int completed;
        private int rescheduled;

        RecordingOutboxService(LambdaAsyncSubmissionOutboxEntry... entries) {
            for (LambdaAsyncSubmissionOutboxEntry entry : entries) this.entries.add(entry);
        }

        @Override
        public void enqueue(int lambdaAssignmentId, long runId, byte[] payload, Throwable failure) {
        }

        @Override
        public LambdaAsyncSubmissionOutboxEntry claimNextDue() {
            return entries.poll();
        }

        @Override
        public boolean completeClaim(LambdaAsyncSubmissionOutboxEntry entry) {
            completed++;
            return true;
        }

        @Override
        public boolean rescheduleClaim(LambdaAsyncSubmissionOutboxEntry entry, Throwable failure) {
            rescheduled++;
            return true;
        }
    }

    private static class RecordingLambdaRunService extends LambdaRunService {
        private long staleRunId;
        private int submissions;
        private RuntimeException failure;

        RecordingLambdaRunService() {
            super(null, null, null, null);
        }

        @Override
        public boolean sendAsyncRequest(LambdaInvokeRequest request) {
            submissions++;
            if (failure != null) throw failure;
            return request.requestId != staleRunId;
        }
    }
}
