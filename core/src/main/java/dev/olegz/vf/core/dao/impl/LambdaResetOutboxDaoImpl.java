package dev.olegz.vf.core.dao.impl;

import java.sql.Timestamp;

import dev.olegz.vf.core.dao.LambdaResetOutboxDao;
import dev.olegz.vf.core.dao.mapper.LambdaResetOutboxMapper;
import dev.olegz.vf.core.domain.lambdarun.LambdaResetOutboxEntry;
import org.springframework.stereotype.Repository;

@Repository
public class LambdaResetOutboxDaoImpl implements LambdaResetOutboxDao {
    private final LambdaResetOutboxMapper mapper;

    public LambdaResetOutboxDaoImpl(LambdaResetOutboxMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(LambdaResetOutboxEntry entry) {
        mapper.insert(entry);
    }

    @Override
    public LambdaResetOutboxEntry claimNextAvailable(Timestamp now, String claimId, Timestamp claimUntil) {
        return mapper.claimNextAvailable(now, claimId, claimUntil);
    }

    @Override
    public boolean deleteClaimed(LambdaResetOutboxEntry entry) {
        return mapper.deleteClaimed(entry);
    }

    @Override
    public boolean releaseClaim(LambdaResetOutboxEntry entry) {
        return mapper.releaseClaim(entry);
    }
}
