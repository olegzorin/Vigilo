package dev.olegz.vf.core.service.lambda;

import dev.olegz.vf.core.domain.lambdarun.LambdaRun;
import dev.olegz.vf.core.domain.lambdarun.LambdaRuntimeAssignment;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;

public interface LambdaRunStateService {
    boolean putNewRun(LambdaRun newRun);

    /** Admits an input in the caller's transaction. */
    boolean putNewRunTransactional(LambdaRun newRun);
    boolean checkRunFinal(int lambdaAssignmentId, InvocationLane lane, long previousRunId);
    /** Advances to the next pending run in the caller's transaction. */
    LambdaRun putNextRunTransactional(
        int lambdaAssignmentId,
        InvocationLane lane,
        LambdaRuntimeAssignment lambdaAssignment,
        long previousRunId);

    LambdaRun.Status updateLambdaRunRetry(
        int lambdaAssignmentId,
        InvocationLane lane,
        long runId,
        int invocationGen,
        long timeout,
        byte[] retryPayload);

    /**
     * Atomically marks the expected run attempt as not started when its start marker is unchanged.
     *
     * @return the current persisted run, including any concurrent run or start-marker change
     */
    LambdaRun markLambdaRunNotStartedIfUnchanged(int lambdaAssignmentId, InvocationLane lane, long runId, int initialInvocationGen);
}
