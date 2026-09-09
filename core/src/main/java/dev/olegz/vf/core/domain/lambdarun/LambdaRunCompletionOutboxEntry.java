package dev.olegz.vf.core.domain.lambdarun;

import java.sql.Timestamp;

/**
 * Durable lambda-run completion waiting to be published.
 */
public class LambdaRunCompletionOutboxEntry {
    public int lambdaAssignmentId;
    public InvocationLane lane;
    public long runId;
    public Timestamp createdAt;
    public String claimId;
    public Timestamp claimUntil;

    public LambdaRunCompletionOutboxEntry() {
    }

    public LambdaRunCompletionOutboxEntry(LambdaRunContext context, Timestamp createdAt) {
        this.lambdaAssignmentId = context.lambdaAssignmentId;
        this.lane = context.lane;
        this.runId = context.requestId;
        this.createdAt = createdAt;
    }
}
