package dev.olegz.vf.core.service.lambda;

import dev.olegz.vf.core.domain.lambdarun.LambdaAsyncSubmissionOutboxEntry;

public interface LambdaAsyncSubmissionOutboxService {
    void enqueue(int lambdaAssignmentId, long runId, byte[] payload, Throwable failure);
    LambdaAsyncSubmissionOutboxEntry claimNextDue();
    boolean completeClaim(LambdaAsyncSubmissionOutboxEntry entry);
    boolean rescheduleClaim(LambdaAsyncSubmissionOutboxEntry entry, Throwable failure);
}
