package dev.olegz.vf.core.domain.lambdarun;

import java.sql.Timestamp;

/**
 * Durable default-lane retry payload waiting to be executed.
 */
public class LambdaInvokeRetryOutboxEntry {
    public int lambdaAssignmentId;
    public InvocationLane lane;
    public long runId;
    public int invocationGen;
    public byte[] payload;
    public Timestamp retryAt;
    public String claimId;
    public Timestamp claimUntil;

    public LambdaInvokeRetryOutboxEntry() {
    }

    public LambdaInvokeRetryOutboxEntry(
        int lambdaAssignmentId,
        InvocationLane lane,
        long runId,
        int invocationGen,
        byte[] payload,
        Timestamp retryAt)
    {
        this.lambdaAssignmentId = lambdaAssignmentId;
        this.lane = lane;
        this.runId = runId;
        this.invocationGen = invocationGen;
        this.payload = payload;
        this.retryAt = retryAt;
    }
}
