package dev.olegz.vf.core.dao.impl;

import java.sql.Timestamp;

import dev.olegz.vf.core.dao.LambdaInvokeRetryOutboxDao;
import dev.olegz.vf.core.dao.mapper.LambdaInvokeRetryOutboxMapper;
import dev.olegz.vf.core.domain.lambdarun.LambdaInvokeRetryOutboxEntry;
import org.springframework.stereotype.Repository;

@Repository
public class LambdaInvokeRetryOutboxDaoImpl implements LambdaInvokeRetryOutboxDao {
    private final LambdaInvokeRetryOutboxMapper mapper;

    public LambdaInvokeRetryOutboxDaoImpl(LambdaInvokeRetryOutboxMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean exists(LambdaInvokeRetryOutboxEntry entry) {
        return mapper.exists(entry);
    }

    @Override
    public void insert(LambdaInvokeRetryOutboxEntry entry) {
        mapper.insert(entry);
    }

    @Override
    public LambdaInvokeRetryOutboxEntry claimNextDue(Timestamp now, String claimId, Timestamp claimUntil) {
        return mapper.claimNextDue(now, claimId, claimUntil);
    }

    @Override
    public boolean renewClaim(LambdaInvokeRetryOutboxEntry entry) {
        return mapper.renewClaim(entry);
    }

    @Override
    public boolean deleteClaimed(LambdaInvokeRetryOutboxEntry entry) {
        return mapper.deleteClaimed(entry);
    }

    @Override
    public boolean releaseClaim(LambdaInvokeRetryOutboxEntry entry) {
        return mapper.releaseClaim(entry);
    }

    @Override
    public void deleteForLambdaAssignment(int lambdaAssignmentId) {
        mapper.deleteForLambdaAssignment(lambdaAssignmentId);
    }
}
