package dev.olegz.vf.core.dao;

import java.sql.Timestamp;

import dev.olegz.vf.core.domain.lambdarun.LambdaAsyncSubmissionOutboxEntry;

public interface LambdaAsyncSubmissionOutboxDao {
    boolean exists(LambdaAsyncSubmissionOutboxEntry entry);
    void insert(LambdaAsyncSubmissionOutboxEntry entry);
    LambdaAsyncSubmissionOutboxEntry claimNextDue(Timestamp now, String claimId, Timestamp claimUntil);
    boolean deleteClaimed(LambdaAsyncSubmissionOutboxEntry entry);
    boolean rescheduleClaimed(LambdaAsyncSubmissionOutboxEntry entry);
    void deleteForLambdaAssignment(int lambdaAssignmentId);
}
