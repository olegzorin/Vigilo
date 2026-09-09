package dev.olegz.vf.worker.scheduler.job;

import dev.olegz.vf.core.service.lambda.LambdaAssignmentCleanupService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CleanupCancelledLambdaAssignmentsJobTest {
    @Test
    void runCleansAllDueAssignments() {
        int[] next = {101};
        LambdaAssignmentCleanupService cleanupService = new LambdaAssignmentCleanupService() {
            @Override
            public void enqueueLambdaAssignmentCleanup(int lambdaAssignmentId) {
                throw new AssertionError("Unexpected enqueue");
            }

            @Override
            public Integer cleanupNextLambdaAssignment() {
                return next[0] <= 103 ? next[0]++ : null;
            }
        };
        CleanupCancelledLambdaAssignmentsJob job = new CleanupCancelledLambdaAssignmentsJob(cleanupService);

        job.run();

        assertEquals(104, next[0]);
        assertEquals("CleanupCancelledLambdaAssignments", job.name());
        assertEquals("0 0 2 * * ?", job.cron());
    }
}
