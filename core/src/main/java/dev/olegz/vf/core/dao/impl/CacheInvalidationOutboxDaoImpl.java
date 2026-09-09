package dev.olegz.vf.core.dao.impl;

import java.sql.Timestamp;

import dev.olegz.vf.core.dao.CacheInvalidationOutboxDao;
import dev.olegz.vf.core.dao.mapper.CacheInvalidationOutboxMapper;
import dev.olegz.vf.core.domain.cache.CacheInvalidationOutboxEntry;
import org.springframework.stereotype.Repository;

@Repository
public class CacheInvalidationOutboxDaoImpl implements CacheInvalidationOutboxDao {
    private final CacheInvalidationOutboxMapper mapper;

    public CacheInvalidationOutboxDaoImpl(CacheInvalidationOutboxMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(CacheInvalidationOutboxEntry entry) {
        mapper.insert(entry);
    }

    @Override
    public CacheInvalidationOutboxEntry claimNextAvailable(Timestamp now, String claimId, Timestamp claimUntil) {
        return mapper.claimNextAvailable(now, claimId, claimUntil);
    }

    @Override
    public boolean deleteClaimed(CacheInvalidationOutboxEntry entry) {
        return mapper.deleteClaimed(entry);
    }

    @Override
    public boolean releaseClaim(CacheInvalidationOutboxEntry entry) {
        return mapper.releaseClaim(entry);
    }
}
