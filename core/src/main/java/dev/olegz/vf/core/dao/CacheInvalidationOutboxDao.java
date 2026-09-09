package dev.olegz.vf.core.dao;

import java.sql.Timestamp;

import dev.olegz.vf.core.domain.cache.CacheInvalidationOutboxEntry;

public interface CacheInvalidationOutboxDao {
    void insert(CacheInvalidationOutboxEntry entry);
    CacheInvalidationOutboxEntry claimNextAvailable(Timestamp now, String claimId, Timestamp claimUntil);
    boolean deleteClaimed(CacheInvalidationOutboxEntry entry);
    boolean releaseClaim(CacheInvalidationOutboxEntry entry);
}
