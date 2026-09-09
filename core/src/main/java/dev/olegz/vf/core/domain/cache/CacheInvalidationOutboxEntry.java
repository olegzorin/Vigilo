package dev.olegz.vf.core.domain.cache;

import java.sql.Timestamp;

/** Durable cache-invalidation payload waiting for acknowledged publication. */
public class CacheInvalidationOutboxEntry {
    public long id;
    public byte[] payload;
    public Timestamp createdAt;
    public String claimId;
    public Timestamp claimUntil;

    public CacheInvalidationOutboxEntry() {
    }

    public CacheInvalidationOutboxEntry(byte[] payload, Timestamp createdAt) {
        this.payload = payload;
        this.createdAt = createdAt;
    }
}
