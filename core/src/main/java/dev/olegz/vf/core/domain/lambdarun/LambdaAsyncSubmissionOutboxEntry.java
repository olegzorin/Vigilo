package dev.olegz.vf.core.domain.lambdarun;

import java.sql.Timestamp;

/**
 * Durable asynchronous Lambda submission waiting to be retried.
 */
public class LambdaAsyncSubmissionOutboxEntry {
    public int lambdaAssignmentId;
    public long runId;
    public byte[] payload;
    public Timestamp retryAt;
    public int attemptCount;
    public String lastError;
    public String claimId;
    public Timestamp claimUntil;

    public LambdaAsyncSubmissionOutboxEntry() {
    }

    public LambdaAsyncSubmissionOutboxEntry(
        int lambdaAssignmentId,
        long runId,
        byte[] payload,
        Timestamp retryAt,
        String lastError)
    {
        this.lambdaAssignmentId = lambdaAssignmentId;
        this.runId = runId;
        this.payload = payload;
        this.retryAt = retryAt;
        this.lastError = lastError;
    }
}
