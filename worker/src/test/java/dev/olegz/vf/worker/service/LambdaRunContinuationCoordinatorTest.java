package dev.olegz.vf.worker.service;

import java.lang.reflect.Proxy;

import dev.olegz.vf.core.domain.lambdarun.LambdaRun;
import dev.olegz.vf.core.domain.lambdarun.LambdaRunContext;
import dev.olegz.vf.core.domain.lambdarun.LambdaRuntimeAssignment;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import dev.olegz.vf.core.service.lambda.LambdaRunStateService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LambdaRunContinuationCoordinatorTest {
    @Test
    void completionPublishesPendingRunBeforeReturningFromStateAdvance() {
        LambdaRun next = new LambdaRun();
        next.lambdaAssignmentId = 17;
        next.invocationLane = InvocationLane.DEFAULT;
        next.runId = 124_000L;
        LambdaRunStateService state = proxy(LambdaRunStateService.class, (proxy, method, args) -> switch (method.getName()) {
            case "checkRunFinal" -> false;
            case "putNextRunTransactional" -> next;
            default -> defaultValue(method.getReturnType());
        });
        LambdaRunContinuationCoordinator coordinator = new LambdaRunContinuationCoordinator(state);
        LambdaRun[] published = new LambdaRun[1];

        LambdaRun result = coordinator.completeAndAdvance(
            context(), new LambdaRuntimeAssignment(), run -> published[0] = run);

        assertSame(next, result);
        assertSame(next, published[0]);
    }

    @Test
    void completedLaneDoesNotPublish() {
        LambdaRunStateService state = proxy(LambdaRunStateService.class, (proxy, method, args) ->
            method.getName().equals("checkRunFinal"));
        LambdaRunContinuationCoordinator coordinator = new LambdaRunContinuationCoordinator(state);
        LambdaRun[] published = new LambdaRun[1];

        assertNull(coordinator.completeAndAdvance(
            context(), new LambdaRuntimeAssignment(), run -> published[0] = run));
        assertNull(published[0]);
    }

    @Test
    void publicationFailureEscapesTheTransactionalBoundary() {
        LambdaRun next = new LambdaRun();
        LambdaRunStateService state = proxy(LambdaRunStateService.class, (proxy, method, args) -> switch (method.getName()) {
            case "checkRunFinal" -> false;
            case "putNextRunTransactional" -> next;
            default -> defaultValue(method.getReturnType());
        });
        LambdaRunContinuationCoordinator coordinator = new LambdaRunContinuationCoordinator(state);
        RuntimeException failure = new RuntimeException("broker unavailable");

        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
            coordinator.completeAndAdvance(
                context(), new LambdaRuntimeAssignment(), run -> { throw failure; }));

        assertSame(failure, thrown);
    }

    private static LambdaRunContext context() {
        LambdaRunContext context = new LambdaRunContext();
        context.lambdaAssignmentId = 17;
        context.lane = InvocationLane.DEFAULT;
        context.requestId = 123_000L;
        return context;
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static Object defaultValue(Class<?> type) {
        return type == boolean.class ? false : type == int.class ? 0 : type == long.class ? 0L : null;
    }
}
