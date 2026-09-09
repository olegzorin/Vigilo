package dev.olegz.vf.core.service.lambda;

import dev.olegz.vf.core.domain.lambdarun.LambdaRunCompletionOutboxEntry;
import dev.olegz.vf.core.domain.lambdarun.LambdaRunContext;

public interface LambdaRunCompletionOutboxService {
    void enqueue(LambdaRunContext context);
    LambdaRunCompletionOutboxEntry claimNextAvailable();
    boolean renewClaim(LambdaRunCompletionOutboxEntry entry);
    boolean completeClaim(LambdaRunCompletionOutboxEntry entry);
    boolean releaseClaim(LambdaRunCompletionOutboxEntry entry);
}
