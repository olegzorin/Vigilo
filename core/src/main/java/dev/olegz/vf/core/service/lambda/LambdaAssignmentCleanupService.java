package dev.olegz.vf.core.service.lambda;

public interface LambdaAssignmentCleanupService {
    void enqueueLambdaAssignmentCleanup(int lambdaAssignmentId);
    Integer cleanupNextLambdaAssignment();
}
