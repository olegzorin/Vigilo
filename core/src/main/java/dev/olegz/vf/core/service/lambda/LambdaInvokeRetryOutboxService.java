package dev.olegz.vf.core.service.lambda;

import dev.olegz.vf.core.domain.lambdarun.LambdaInvokeRetryOutboxEntry;

public interface LambdaInvokeRetryOutboxService {
    LambdaInvokeRetryOutboxEntry claimNextDue();
    boolean renewClaim(LambdaInvokeRetryOutboxEntry entry);
    boolean completeClaim(LambdaInvokeRetryOutboxEntry entry);
    boolean releaseClaim(LambdaInvokeRetryOutboxEntry entry);
}
