package dev.olegz.vf.core.dao;

import java.sql.Timestamp;
import java.util.List;

import dev.olegz.vf.core.domain.lambdarun.LambdaPendingInputRecord;
import dev.olegz.vf.core.domain.lambdarun.LambdaRun;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;

/** Persistence operations used by the transactional lambda-run state machine. */
public interface LambdaRunStateDao {
    LambdaRun getLambdaRunForUpdate(int lambdaAssignmentId, InvocationLane lane);
    void insertLambdaRun(LambdaRun lambdaRun);
    boolean updateLambdaRun(LambdaRun lambdaRun);
    void enqueuePendingInput(int lambdaAssignmentId, InvocationLane lane, LambdaPendingInputRecord input,
        boolean large, int paddedSize, long nextInputNumber, int part);
    List<LambdaPendingInputRecord> getPendingInputs(int lambdaAssignmentId, InvocationLane lane, long nextInputNumber,
        int maxCount, boolean sorted, String partitions);
    void updateLambdaRunPendingCount(int lambdaAssignmentId, InvocationLane lane, int pendingCount, long nextInputNumber);
    boolean markLambdaRunComplete(int lambdaAssignmentId, InvocationLane lane, Long previousRunId);
    void updateLambdaRunForRetry(int lambdaAssignmentId, InvocationLane lane, int invocationGen, Timestamp expiryDate);
    void markLambdaRunNotStarted(int lambdaAssignmentId, InvocationLane lane);
}
