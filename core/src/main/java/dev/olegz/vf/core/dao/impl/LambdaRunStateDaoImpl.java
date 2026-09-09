package dev.olegz.vf.core.dao.impl;

import java.sql.Timestamp;
import java.util.List;

import dev.olegz.vf.core.dao.LambdaRunStateDao;
import dev.olegz.vf.core.dao.mapper.LambdaRunMapper;
import dev.olegz.vf.core.domain.lambdarun.LambdaPendingInputRecord;
import dev.olegz.vf.core.domain.lambdarun.LambdaRun;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import org.springframework.stereotype.Repository;

@Repository
public class LambdaRunStateDaoImpl implements LambdaRunStateDao {
    private final LambdaRunMapper mapper;

    public LambdaRunStateDaoImpl(LambdaRunMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public LambdaRun getLambdaRunForUpdate(int lambdaAssignmentId, InvocationLane lane) {
        return mapper.selectLambdaRunForUpdate(lambdaAssignmentId, lane);
    }

    @Override
    public void insertLambdaRun(LambdaRun lambdaRun) {
        mapper.insertLambdaRun(lambdaRun);
    }

    @Override
    public boolean updateLambdaRun(LambdaRun lambdaRun) {
        return mapper.updateLambdaRun(lambdaRun);
    }

    @Override
    public void enqueuePendingInput(int lambdaAssignmentId, InvocationLane lane, LambdaPendingInputRecord input,
        boolean large, int paddedSize, long nextInputNumber, int part)
    {
        if (!mapper.updateLambdaPendingInput(lambdaAssignmentId, lane, input, large, paddedSize,
            nextInputNumber, part))
        {
            mapper.insertLambdaPendingInput(lambdaAssignmentId, lane, input, large, paddedSize, part);
        }
    }

    @Override
    public List<LambdaPendingInputRecord> getPendingInputs(int lambdaAssignmentId, InvocationLane lane,
        long nextInputNumber, int maxCount, boolean sorted, String partitions)
    {
        return mapper.selectLambdaPendingInputs(lambdaAssignmentId, lane, nextInputNumber, maxCount, sorted, partitions);
    }

    @Override
    public void updateLambdaRunPendingCount(int lambdaAssignmentId, InvocationLane lane, int pendingCount,
        long nextInputNumber)
    {
        mapper.updateLambdaRunPendingCount(lambdaAssignmentId, lane, pendingCount, nextInputNumber);
    }

    @Override
    public boolean markLambdaRunComplete(int lambdaAssignmentId, InvocationLane lane, Long previousRunId) {
        return mapper.updateLambdaRunAsComplete(lambdaAssignmentId, lane, previousRunId);
    }

    @Override
    public void updateLambdaRunForRetry(int lambdaAssignmentId, InvocationLane lane, int invocationGen, Timestamp expiryDate) {
        mapper.updateLambdaRunForRetry(lambdaAssignmentId, lane, invocationGen, expiryDate);
    }

    @Override
    public void markLambdaRunNotStarted(int lambdaAssignmentId, InvocationLane lane) {
        mapper.updateLambdaRunAsNotStarted(lambdaAssignmentId, lane);
    }
}
