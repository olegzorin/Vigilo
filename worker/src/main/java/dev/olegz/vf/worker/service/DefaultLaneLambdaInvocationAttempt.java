package dev.olegz.vf.worker.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.core.domain.lambdarun.LambdaRun;
import dev.olegz.vf.core.domain.lambdarun.LambdaRunInfo;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import dev.olegz.vf.core.service.lambda.LambdaRunStateService;
import dev.olegz.vf.worker.domain.LambdaInvocationOutcome;
import dev.olegz.vf.worker.domain.LambdaInvokeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates one synchronous invocation attempt in the default lane.
 */
class DefaultLaneLambdaInvocationAttempt {
    private static final Logger logger = LoggerFactory.getLogger(DefaultLaneLambdaInvocationAttempt.class);

    private enum SignalType {
        INVOCATION_COMPLETED,
        RUN_ALREADY_COMPLETED,
        OTHER_RUN_STARTED,
        LAMBDA_NOT_STARTED,
        NO_RESPONSE
    }

    private record AttemptSignal(SignalType type) {
    }

    private final LambdaInvokeRequest request;
    private final LambdaRunStateService lambdaRunStateService;
    private final ScheduledExecutorService timeoutScheduler;
    private final long startTimeout;
    private final TimeUnit startTimeoutUnit;
    private final AtomicReference<ScheduledFuture<?>> startCheckTask = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> expiryTask = new AtomicReference<>();

    DefaultLaneLambdaInvocationAttempt(LambdaInvokeRequest request, LambdaRunStateService lambdaRunStateService,
        ScheduledExecutorService timeoutScheduler, long startTimeout, TimeUnit startTimeoutUnit)
    {
        this.request = request;
        this.lambdaRunStateService = lambdaRunStateService;
        this.timeoutScheduler = timeoutScheduler;
        this.startTimeout = startTimeout;
        this.startTimeoutUnit = startTimeoutUnit;
    }

    CompletableFuture<LambdaInvocationOutcome> coordinate(CompletableFuture<Void> invocationFuture) {
        CompletableFuture<AttemptSignal> signalFuture = new CompletableFuture<>();
        invocationFuture.whenComplete((_, error) -> {
            if (error != null) {
                signalFuture.completeExceptionally(error);
            } else {
                signalFuture.complete(new AttemptSignal(SignalType.INVOCATION_COMPLETED));
            }
        });

        ScheduledFuture<?> scheduledStartCheck = timeoutScheduler.schedule(
            () -> checkRunStart(signalFuture), startTimeout, startTimeoutUnit);
        startCheckTask.set(scheduledStartCheck);
        if (signalFuture.isDone()) scheduledStartCheck.cancel(false);

        CompletableFuture<LambdaInvocationOutcome> outcomeFuture = signalFuture.thenApply(this::resolve);
        outcomeFuture.whenComplete((_, _) -> cancelTimers());
        return outcomeFuture;
    }

    private void checkRunStart(CompletableFuture<AttemptSignal> signalFuture) {
        if (signalFuture.isDone()) return;

        try {
            LambdaRun lambdaRun = lambdaRunStateService.markLambdaRunNotStartedIfUnchanged(
                request.lambdaAssignmentId, InvocationLane.DEFAULT, request.requestId, request.invocationGen);

            if (logger.isDebugEnabled()) {
                logger.debug("checkRunStart() " + request.toLogString() + ", lambdaRun={runId=" + lambdaRun.runId +
                    ", started=" + lambdaRun.started + ", invocationGen=" + lambdaRun.invocationGen +
                    ", status=" + lambdaRun.status + '}');
            }

            if (request.requestId != lambdaRun.runId) {
                SignalType type = (lambdaRun.prevRunId != null) && (lambdaRun.prevRunId == request.requestId) ?
                    SignalType.RUN_ALREADY_COMPLETED : SignalType.OTHER_RUN_STARTED;
                signalFuture.complete(new AttemptSignal(type));
            } else if (!lambdaRun.started) {
                signalFuture.complete(new AttemptSignal(SignalType.LAMBDA_NOT_STARTED));
            } else {
                scheduleExpiry(signalFuture, lambdaRun);
            }
        } catch (Throwable error) {
            signalFuture.completeExceptionally(error);
        }
    }

    private void scheduleExpiry(CompletableFuture<AttemptSignal> signalFuture, LambdaRun lambdaRun) {
        long delay = lambdaRun.expiryDate.getTime() - System.currentTimeMillis();
        if (delay <= 10) {
            signalFuture.complete(new AttemptSignal(SignalType.NO_RESPONSE));
            return;
        }

        if (signalFuture.isDone()) return;
        ScheduledFuture<?> scheduledExpiry = timeoutScheduler.schedule(
            () -> signalFuture.complete(new AttemptSignal(SignalType.NO_RESPONSE)), delay, TimeUnit.MILLISECONDS);
        expiryTask.set(scheduledExpiry);
        if (signalFuture.isDone()) scheduledExpiry.cancel(false);
    }

    private LambdaInvocationOutcome resolve(AttemptSignal signal) {
        if (logger.isDebugEnabled()) {
            logger.debug("resolve() " + request.toLogString() + ", signal=" + signal.type);
        }

        return switch (signal.type) {
            case INVOCATION_COMPLETED -> resolveInvocationCompletion();
            case RUN_ALREADY_COMPLETED -> LambdaInvocationOutcome.acknowledgeOnly(request);
            case OTHER_RUN_STARTED -> {
                LambdaInvokeRequest result = request.copyForOutcome();
                result.setResult(LambdaRunInfo.OTHER_RUN_STARTED, "Other run");
                yield LambdaInvocationOutcome.finalResult(result);
            }
            case LAMBDA_NOT_STARTED -> {
                LambdaInvokeRequest result = request.copyForOutcome();
                result.setResult(LambdaRunInfo.LAMBDA_NOT_STARTED, "Lambda was not started");
                yield result.canRetry() ? resolveRetry(result) : LambdaInvocationOutcome.finalResult(result);
            }
            case NO_RESPONSE -> {
                LambdaInvokeRequest result = request.copyForOutcome();
                result.setResult(LambdaRunInfo.REQUEST_TIMEOUT, "Lambda was running but the response is timed out");
                yield LambdaInvocationOutcome.finalResult(result);
            }
        };
    }

    private LambdaInvocationOutcome resolveInvocationCompletion() {
        if (request.resultCode == LambdaRunInfo.DUPLICATE_REQUEST) {
            return LambdaInvocationOutcome.acknowledgeOnly(request);
        }

        if (request.shouldRetry && request.canRetry()) {
            return resolveRetry(request);
        }

        return LambdaInvocationOutcome.finalResult(request);
    }

    private LambdaInvocationOutcome resolveRetry(LambdaInvokeRequest failedAttempt) {
        LambdaInvokeRequest retry = failedAttempt.createRetryAttempt();
        LambdaRun.Status status = lambdaRunStateService.updateLambdaRunRetry(
            retry.lambdaAssignmentId, InvocationLane.DEFAULT, retry.requestId, retry.invocationGen, retry.timeout,
            BytesMapper.writeValue(retry));

        return switch (status) {
            case RETRY -> LambdaInvocationOutcome.retryPersisted(retry);
            case COMPLETED -> LambdaInvocationOutcome.acknowledgeOnly(failedAttempt);
            case OTHER -> {
                failedAttempt.setResult(LambdaRunInfo.OTHER_RUN_STARTED, "Other run");
                yield LambdaInvocationOutcome.finalResult(failedAttempt);
            }
            case TOO_MANY -> LambdaInvocationOutcome.finalResult(failedAttempt);
            case NEW, NEXT -> {
                logger.warn("Unexpected status when updating lambda run retry: status=" + status + ", " +
                    failedAttempt.toLogString());
                yield LambdaInvocationOutcome.finalResult(failedAttempt);
            }
        };
    }

    private void cancelTimers() {
        cancel(startCheckTask.get());
        cancel(expiryTask.get());
    }

    private static void cancel(ScheduledFuture<?> task) {
        if ((task != null) && !task.isDone()) task.cancel(false);
    }
}
