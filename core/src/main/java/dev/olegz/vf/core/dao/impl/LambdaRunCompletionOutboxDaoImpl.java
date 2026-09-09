package dev.olegz.vf.core.dao.impl;

import java.sql.Timestamp;

import dev.olegz.vf.core.dao.LambdaRunCompletionOutboxDao;
import dev.olegz.vf.core.dao.mapper.LambdaRunCompletionOutboxMapper;
import dev.olegz.vf.core.domain.lambdarun.LambdaRunCompletionOutboxEntry;
import org.springframework.stereotype.Repository;

@Repository
public class LambdaRunCompletionOutboxDaoImpl implements LambdaRunCompletionOutboxDao {
    private final LambdaRunCompletionOutboxMapper mapper;

    public LambdaRunCompletionOutboxDaoImpl(LambdaRunCompletionOutboxMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(LambdaRunCompletionOutboxEntry entry) {
        mapper.insert(entry);
    }

    @Override
    public LambdaRunCompletionOutboxEntry claimNextAvailable(Timestamp now, String claimId, Timestamp claimUntil) {
        return mapper.claimNextAvailable(now, claimId, claimUntil);
    }

    @Override
    public boolean renewClaim(LambdaRunCompletionOutboxEntry entry) {
        return mapper.renewClaim(entry);
    }

    @Override
    public boolean deleteClaimed(LambdaRunCompletionOutboxEntry entry) {
        return mapper.deleteClaimed(entry);
    }

    @Override
    public boolean releaseClaim(LambdaRunCompletionOutboxEntry entry) {
        return mapper.releaseClaim(entry);
    }

    @Override
    public void deleteForLambdaAssignment(int lambdaAssignmentId) {
        mapper.deleteForLambdaAssignment(lambdaAssignmentId);
    }
}
