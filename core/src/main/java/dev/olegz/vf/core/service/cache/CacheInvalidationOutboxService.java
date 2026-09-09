package dev.olegz.vf.core.service.cache;

import dev.olegz.vf.core.domain.cache.CacheInvalidationOutboxEntry;

public interface CacheInvalidationOutboxService {
    /** Insert into the caller's transaction, or create a transaction when invoked without one. */
    void enqueue(byte[] payload);

    /** Claim one row in an independent short transaction. */
    CacheInvalidationOutboxEntry claimNext();
    boolean completeClaim(CacheInvalidationOutboxEntry entry);
    boolean releaseClaim(CacheInvalidationOutboxEntry entry);
}
