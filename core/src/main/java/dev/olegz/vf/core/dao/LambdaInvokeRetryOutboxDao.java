package dev.olegz.vf.core.dao;

import java.sql.Timestamp;

import dev.olegz.vf.core.domain.lambdarun.LambdaInvokeRetryOutboxEntry;

public interface LambdaInvokeRetryOutboxDao {
    boolean exists(LambdaInvokeRetryOutboxEntry entry);
    void insert(LambdaInvokeRetryOutboxEntry entry);
    LambdaInvokeRetryOutboxEntry claimNextDue(Timestamp now, String claimId, Timestamp claimUntil);
    boolean renewClaim(LambdaInvokeRetryOutboxEntry entry);
    boolean deleteClaimed(LambdaInvokeRetryOutboxEntry entry);
    boolean releaseClaim(LambdaInvokeRetryOutboxEntry entry);
    void deleteForLambdaAssignment(int lambdaAssignmentId);
}
