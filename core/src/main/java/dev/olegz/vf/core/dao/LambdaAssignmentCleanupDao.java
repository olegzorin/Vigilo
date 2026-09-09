package dev.olegz.vf.core.dao;

import dev.olegz.vf.common.Datetime;

public interface LambdaAssignmentCleanupDao {
    void enqueueLambdaAssignmentCleanup(int lambdaAssignmentId, Datetime cleanupAfter);
    Integer takeNextLambdaAssignmentForCleanup(Datetime date);
}
