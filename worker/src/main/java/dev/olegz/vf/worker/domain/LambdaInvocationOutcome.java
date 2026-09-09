package dev.olegz.vf.worker.domain;

import java.util.Objects;

/**
 * Terminal action for one default-lane lambda invocation attempt.
 */
public record LambdaInvocationOutcome(Action action, LambdaInvokeRequest request) {

    public enum Action {
        FINAL_RESULT,
        RETRY_PERSISTED,
        ACKNOWLEDGE_ONLY
    }

    public LambdaInvocationOutcome {
        Objects.requireNonNull(action);
        Objects.requireNonNull(request);
    }

    public static LambdaInvocationOutcome finalResult(LambdaInvokeRequest request) {
        return new LambdaInvocationOutcome(Action.FINAL_RESULT, request);
    }

    public static LambdaInvocationOutcome retryPersisted(LambdaInvokeRequest request) {
        return new LambdaInvocationOutcome(Action.RETRY_PERSISTED, request);
    }

    public static LambdaInvocationOutcome acknowledgeOnly(LambdaInvokeRequest request) {
        return new LambdaInvocationOutcome(Action.ACKNOWLEDGE_ONLY, request);
    }
}
