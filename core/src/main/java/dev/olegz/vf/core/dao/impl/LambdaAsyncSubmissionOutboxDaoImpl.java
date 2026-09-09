package dev.olegz.vf.core.dao.impl;

import java.sql.Timestamp;

import dev.olegz.vf.core.dao.LambdaAsyncSubmissionOutboxDao;
import dev.olegz.vf.core.dao.mapper.LambdaAsyncSubmissionOutboxMapper;
import dev.olegz.vf.core.domain.lambdarun.LambdaAsyncSubmissionOutboxEntry;
import org.springframework.stereotype.Repository;

@Repository
public class LambdaAsyncSubmissionOutboxDaoImpl implements LambdaAsyncSubmissionOutboxDao {
    private final LambdaAsyncSubmissionOutboxMapper mapper;

    public LambdaAsyncSubmissionOutboxDaoImpl(LambdaAsyncSubmissionOutboxMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean exists(LambdaAsyncSubmissionOutboxEntry entry) {
        return mapper.exists(entry);
    }

    @Override
    public void insert(LambdaAsyncSubmissionOutboxEntry entry) {
        mapper.insert(entry);
    }

    @Override
    public LambdaAsyncSubmissionOutboxEntry claimNextDue(Timestamp now, String claimId, Timestamp claimUntil) {
        return mapper.claimNextDue(now, claimId, claimUntil);
    }

    @Override
    public boolean deleteClaimed(LambdaAsyncSubmissionOutboxEntry entry) {
        return mapper.deleteClaimed(entry);
    }

    @Override
    public boolean rescheduleClaimed(LambdaAsyncSubmissionOutboxEntry entry) {
        return mapper.rescheduleClaimed(entry);
    }

    @Override
    public void deleteForLambdaAssignment(int lambdaAssignmentId) {
        mapper.deleteForLambdaAssignment(lambdaAssignmentId);
    }
}
