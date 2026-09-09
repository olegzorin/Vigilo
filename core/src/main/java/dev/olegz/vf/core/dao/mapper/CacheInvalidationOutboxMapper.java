package dev.olegz.vf.core.dao.mapper;

import java.sql.Timestamp;

import dev.olegz.vf.core.domain.cache.CacheInvalidationOutboxEntry;
import org.apache.ibatis.annotations.Param;

public interface CacheInvalidationOutboxMapper {
    void insert(CacheInvalidationOutboxEntry entry);
    CacheInvalidationOutboxEntry claimNextAvailable(
        @Param("now") Timestamp now,
        @Param("claimId") String claimId,
        @Param("claimUntil") Timestamp claimUntil);
    boolean deleteClaimed(CacheInvalidationOutboxEntry entry);
    boolean releaseClaim(CacheInvalidationOutboxEntry entry);
}
