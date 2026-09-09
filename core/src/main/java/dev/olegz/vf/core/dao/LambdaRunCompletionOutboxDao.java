package dev.olegz.vf.core.dao;

import java.sql.Timestamp;

import dev.olegz.vf.core.domain.lambdarun.LambdaRunCompletionOutboxEntry;

public interface LambdaRunCompletionOutboxDao {
    void insert(LambdaRunCompletionOutboxEntry entry);
    LambdaRunCompletionOutboxEntry claimNextAvailable(Timestamp now, String claimId, Timestamp claimUntil);
    boolean renewClaim(LambdaRunCompletionOutboxEntry entry);
    boolean deleteClaimed(LambdaRunCompletionOutboxEntry entry);
    boolean releaseClaim(LambdaRunCompletionOutboxEntry entry);
    void deleteForLambdaAssignment(int lambdaAssignmentId);
}
