package dev.olegz.vf.worker.service;

import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import dev.olegz.vf.core.domain.lambdarun.LambdaRun;
import dev.olegz.vf.core.domain.lambdarun.LambdaRunInfo;
import dev.olegz.vf.core.service.lambda.LambdaRunStateService;
import dev.olegz.vf.worker.domain.LambdaInvocationOutcome;
import dev.olegz.vf.worker.domain.LambdaInvokeRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefaultLaneLambdaInvocationAttemptTest {
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);

    DefaultLaneLambdaInvocationAttemptTest() {
        scheduler.setRemoveOnCancelPolicy(true);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void duplicateInvocationIsAcknowledgedWithoutProducingAnotherResult() throws Exception {
        LambdaInvokeRequest request = request();
        request.resultCode = LambdaRunInfo.DUPLICATE_REQUEST;
        DefaultLaneLambdaInvocationAttempt attempt = attempt(request, stateService(null, null), 1, TimeUnit.DAYS);

        LambdaInvocationOutcome outcome = attempt.coordinate(CompletableFuture.completedFuture(null)).get();

        assertEquals(LambdaInvocationOutcome.Action.ACKNOWLEDGE_ONLY, outcome.action());
        assertEquals(request, outcome.request());
    }

    @Test
    void completedRunRaceIsAcknowledged() throws Exception {
        LambdaInvokeRequest request = request();
        LambdaRun completed = run(request.requestId + 1, false);
        completed.prevRunId = request.requestId;
        DefaultLaneLambdaInvocationAttempt attempt =
            attempt(request, stateService(completed, null), 0, TimeUnit.MILLISECONDS);

        LambdaInvocationOutcome outcome = attempt.coordinate(new CompletableFuture<>()).get();

        assertEquals(LambdaInvocationOutcome.Action.ACKNOWLEDGE_ONLY, outcome.action());
    }

    @Test
    void retryUsesDetachedRequestThatLateCompletionCannotMutate() throws Exception {
        LambdaInvokeRequest request = request();
        LambdaRun notStarted = run(request.requestId, false);
        DefaultLaneLambdaInvocationAttempt attempt =
            attempt(request, stateService(notStarted, LambdaRun.Status.RETRY), 0, TimeUnit.MILLISECONDS);

        LambdaInvocationOutcome outcome = attempt.coordinate(new CompletableFuture<>()).get();
        LambdaInvokeRequest retry = outcome.request();

        assertEquals(LambdaInvocationOutcome.Action.RETRY_PERSISTED, outcome.action());
        assertNotSame(request, retry);
        assertEquals(request.invocationGen + 1, retry.invocationGen);
        assertEquals(request.retryAttempted + 1, retry.retryAttempted);
        assertTrue(retry.appInput.contains(Long.toString(
            LambdaRun.makeInvocationToken(retry.requestId, retry.invocationGen))));

        request.setResult(LambdaRunInfo.CLIENT_FAILURE, "late result");
        request.shouldRetry = true;
        assertEquals(0, retry.resultCode);
        assertFalse(retry.shouldRetry);
    }

    @Test
    void completedRetryRaceIsAcknowledged() throws Exception {
        LambdaInvokeRequest request = request();
        LambdaRun notStarted = run(request.requestId, false);
        DefaultLaneLambdaInvocationAttempt attempt =
            attempt(request, stateService(notStarted, LambdaRun.Status.COMPLETED), 0, TimeUnit.MILLISECONDS);

        LambdaInvocationOutcome outcome = attempt.coordinate(new CompletableFuture<>()).get();

        assertEquals(LambdaInvocationOutcome.Action.ACKNOWLEDGE_ONLY, outcome.action());
    }

    @Test
    void stateCheckFailureCompletesAttemptExceptionally() {
        LambdaInvokeRequest request = request();
        LambdaRunStateService service = stateService(null, null);
        RuntimeException failure = new RuntimeException("state unavailable");
        service = (LambdaRunStateService) Proxy.newProxyInstance(
            LambdaRunStateService.class.getClassLoader(),
            new Class<?>[]{LambdaRunStateService.class},
            (_, method, _) -> {
                if (method.getName().equals("markLambdaRunNotStartedIfUnchanged")) throw failure;
                return defaultValue(method.getReturnType());
            });
        DefaultLaneLambdaInvocationAttempt attempt = attempt(request, service, 0, TimeUnit.MILLISECONDS);

        ExecutionException error = assertThrows(ExecutionException.class,
            () -> attempt.coordinate(new CompletableFuture<>()).get());

        assertEquals(failure, error.getCause());
    }

    @Test
    void invocationCompletionCancelsScheduledExpiry() throws Exception {
        LambdaInvokeRequest request = request();
        LambdaRun started = run(request.requestId, true);
        started.expiryDate = new Timestamp(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5));
        CompletableFuture<Void> invocation = new CompletableFuture<>();
        DefaultLaneLambdaInvocationAttempt attempt =
            attempt(request, stateService(started, null), 0, TimeUnit.MILLISECONDS);

        CompletableFuture<LambdaInvocationOutcome> outcome = attempt.coordinate(invocation);
        awaitQueueSize(1);
        invocation.complete(null);

        assertEquals(LambdaInvocationOutcome.Action.FINAL_RESULT, outcome.get().action());
        awaitQueueSize(0);
    }

    private DefaultLaneLambdaInvocationAttempt attempt(LambdaInvokeRequest request, LambdaRunStateService service,
        long timeout, TimeUnit unit)
    {
        return new DefaultLaneLambdaInvocationAttempt(request, service, scheduler, timeout, unit);
    }

    private static LambdaInvokeRequest request() {
        LambdaInvokeRequest request = new LambdaInvokeRequest();
        request.requestId = 1_750_000_000_000L;
        request.invocationGen = 2;
        request.lambdaAssignmentId = 17;
        request.lambdaId = 4;
        request.timeout = 30_000;
        request.appInput = "{\"invocationToken\":" +
            LambdaRun.makeInvocationToken(request.requestId, request.invocationGen) + '}';
        return request;
    }

    private static LambdaRun run(long runId, boolean started) {
        LambdaRun run = new LambdaRun();
        run.runId = runId;
        run.started = started;
        run.expiryDate = new Timestamp(System.currentTimeMillis() + 1000);
        return run;
    }

    private static LambdaRunStateService stateService(LambdaRun checkedRun, LambdaRun.Status retryStatus) {
        return (LambdaRunStateService) Proxy.newProxyInstance(
            LambdaRunStateService.class.getClassLoader(),
            new Class<?>[]{LambdaRunStateService.class},
            (_, method, _) -> switch (method.getName()) {
                case "markLambdaRunNotStartedIfUnchanged" -> checkedRun;
                case "updateLambdaRunRetry" -> retryStatus;
                default -> defaultValue(method.getReturnType());
            });
    }

    private void awaitQueueSize(int expectedSize) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while ((scheduler.getQueue().size() != expectedSize) && (System.nanoTime() < deadline)) {
            Thread.sleep(1);
        }
        assertEquals(expectedSize, scheduler.getQueue().size());
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
