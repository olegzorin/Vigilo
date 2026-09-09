package dev.olegz.vf.worker.service;

import java.sql.Timestamp;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.domain.lambdarun.LambdaInvokeRetryOutboxEntry;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import dev.olegz.vf.core.service.lambda.LambdaInvokeRetryOutboxService;
import dev.olegz.vf.worker.domain.LambdaInvocationOutcome;
import dev.olegz.vf.worker.domain.LambdaInvokeRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class LambdaInvokeRetryOutboxDispatcherTest {
    private LambdaInvokeRetryOutboxDispatcher dispatcher;
    private RecordingLambdaRunService lambdaRunService;

    @BeforeAll
    static void oneTimeSetUp() {
        PropertyStore.start();
    }

    @AfterEach
    void tearDown() {
        if (dispatcher != null) dispatcher.shutdown();
        if (lambdaRunService != null) lambdaRunService.shutdown();
    }

    @Test
    void executesDueRetriesDirectlyAndCompletesTheirClaims() {
        LambdaInvokeRetryOutboxEntry first = entry(1);
        LambdaInvokeRetryOutboxEntry second = entry(2);
        RecordingOutboxService outbox = new RecordingOutboxService(first, second);
        lambdaRunService = new RecordingLambdaRunService();
        lambdaRunService.outcome = CompletableFuture.completedFuture(
            LambdaInvocationOutcome.acknowledgeOnly(request(1)));
        dispatcher = dispatcher(outbox);

        int count = dispatcher.dispatchDueRetries();

        assertEquals(2, count);
        assertEquals(2, lambdaRunService.invocations);
        assertEquals(2, outbox.completedCount);
        assertEquals(0, outbox.releasedCount);
    }

    @Test
    void keepsClaimUntilAsynchronousInvocationFinishes() {
        LambdaInvokeRetryOutboxEntry entry = entry(1);
        RecordingOutboxService outbox = new RecordingOutboxService(entry);
        lambdaRunService = new RecordingLambdaRunService();
        lambdaRunService.outcome = new CompletableFuture<>();
        dispatcher = dispatcher(outbox);

        assertEquals(1, dispatcher.dispatchDueRetries());
        assertEquals(0, outbox.completedCount);

        lambdaRunService.outcome.complete(LambdaInvocationOutcome.acknowledgeOnly(request(1)));

        assertEquals(1, outbox.completedCount);
        assertEquals(0, outbox.releasedCount);
    }

    @Test
    void failedInvocationReleasesClaimForCrashSafeRedelivery() {
        LambdaInvokeRetryOutboxEntry entry = entry(1);
        RecordingOutboxService outbox = new RecordingOutboxService(entry);
        lambdaRunService = new RecordingLambdaRunService();
        lambdaRunService.outcome = CompletableFuture.failedFuture(new RuntimeException("invocation failed"));
        dispatcher = dispatcher(outbox);

        assertEquals(1, dispatcher.dispatchDueRetries());

        assertEquals(0, outbox.completedCount);
        assertEquals(1, outbox.releasedCount);
        assertSame(entry, outbox.lastReleased);
    }

    @Test
    void executorRejectionReleasesClaimAndStopsCurrentBatch() {
        LambdaInvokeRetryOutboxEntry first = entry(1);
        RecordingOutboxService outbox = new RecordingOutboxService(first, entry(2));
        lambdaRunService = new RecordingLambdaRunService();
        lambdaRunService.reject = true;
        dispatcher = dispatcher(outbox);

        assertEquals(0, dispatcher.dispatchDueRetries());

        assertEquals(1, lambdaRunService.invocations);
        assertEquals(0, outbox.completedCount);
        assertEquals(1, outbox.releasedCount);
        assertSame(first, outbox.lastReleased);
    }

    private LambdaInvokeRetryOutboxDispatcher dispatcher(RecordingOutboxService outbox) {
        return new LambdaInvokeRetryOutboxDispatcher(
            outbox,
            lambdaRunService,
            new ScheduledThreadPoolExecutor(1));
    }

    private static LambdaInvokeRetryOutboxEntry entry(int invocationGen) {
        LambdaInvokeRequest request = request(invocationGen);
        LambdaInvokeRetryOutboxEntry entry = new LambdaInvokeRetryOutboxEntry(
            request.lambdaAssignmentId,
            request.lane,
            request.requestId,
            invocationGen,
            BytesMapper.writeValue(request),
            new Timestamp(System.currentTimeMillis()));
        entry.claimId = "claim-" + invocationGen;
        return entry;
    }

    private static LambdaInvokeRequest request(int invocationGen) {
        LambdaInvokeRequest request = new LambdaInvokeRequest();
        request.requestId = 1_750_000_000_000L;
        request.lambdaAssignmentId = 17;
        request.lambdaId = 4;
        request.invocationGen = invocationGen;
        request.lambdaFunction = "test-function";
        request.appInput = "{\"invocationToken\":1750000000000000}";
        request.lane = InvocationLane.DEFAULT;
        request.timeout = 30_000;
        return request;
    }

    private static class RecordingOutboxService implements LambdaInvokeRetryOutboxService {
        private final Queue<LambdaInvokeRetryOutboxEntry> entries = new ArrayDeque<>();
        private int completedCount;
        private int releasedCount;
        private LambdaInvokeRetryOutboxEntry lastReleased;

        RecordingOutboxService(LambdaInvokeRetryOutboxEntry... entries) {
            for (LambdaInvokeRetryOutboxEntry entry : entries) {
                this.entries.add(entry);
            }
        }

        @Override
        public LambdaInvokeRetryOutboxEntry claimNextDue() {
            return entries.poll();
        }

        @Override
        public boolean renewClaim(LambdaInvokeRetryOutboxEntry entry) {
            return true;
        }

        @Override
        public boolean completeClaim(LambdaInvokeRetryOutboxEntry entry) {
            completedCount++;
            return true;
        }

        @Override
        public boolean releaseClaim(LambdaInvokeRetryOutboxEntry entry) {
            releasedCount++;
            lastReleased = entry;
            return true;
        }
    }

    private static class RecordingLambdaRunService extends LambdaRunService {
        private CompletableFuture<LambdaInvocationOutcome> outcome;
        private int invocations;
        private boolean reject;

        RecordingLambdaRunService() {
            super(null, null, null, null);
        }

        @Override
        public CompletableFuture<LambdaInvocationOutcome> executeDefaultLaneLambdaRequest(LambdaInvokeRequest request) {
            invocations++;
            if (reject) throw new RejectedExecutionException("busy");
            return outcome;
        }

        @Override
        public void shutdown() {
        }
    }
}
