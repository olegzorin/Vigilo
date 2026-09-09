package dev.olegz.vf.core.dao.impl;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.core.dao.LambdaAssignmentCleanupDao;
import dev.olegz.vf.core.dao.mapper.LambdaAssignmentCleanupMapper;
import org.springframework.stereotype.Repository;

@Repository
public class LambdaAssignmentCleanupDaoImpl implements LambdaAssignmentCleanupDao {
    private final LambdaAssignmentCleanupMapper mapper;

    public LambdaAssignmentCleanupDaoImpl(LambdaAssignmentCleanupMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void enqueueLambdaAssignmentCleanup(int lambdaAssignmentId, Datetime cleanupAfter) {
        mapper.insertLambdaAssignmentCleanup(lambdaAssignmentId, cleanupAfter);
    }

    @Override
    public Integer takeNextLambdaAssignmentForCleanup(Datetime date) {
        return mapper.takeNextLambdaAssignmentForCleanup(date);
    }
}
