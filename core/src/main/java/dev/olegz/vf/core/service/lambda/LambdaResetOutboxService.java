package dev.olegz.vf.core.service.lambda;

import dev.olegz.vf.core.domain.lambdarun.LambdaResetOutboxEntry;
import dev.olegz.vf.core.event.ResetEvent;

public interface LambdaResetOutboxService {
    /** Insert into the caller's transaction, or create one when invoked without a transaction. */
    void enqueue(ResetEvent event);

    LambdaResetOutboxEntry claimNext();
    boolean completeClaim(LambdaResetOutboxEntry entry);
    boolean releaseClaim(LambdaResetOutboxEntry entry);
}
