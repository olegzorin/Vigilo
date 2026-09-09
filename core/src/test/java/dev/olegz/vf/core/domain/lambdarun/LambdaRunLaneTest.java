package dev.olegz.vf.core.domain.lambdarun;

import java.util.List;

import dev.olegz.vf.core.domain.lambdarun.input.TriggerEventData;
import dev.olegz.vf.core.event.ScheduledEvent;
import dev.olegz.vf.core.event.TriggerEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LambdaRunLaneTest {
    @Test
    void placeholderRunRecordsWhenItWasTriggered() {
        LambdaRun run = new LambdaRun(7, InvocationLane.DEFAULT);

        assertAll(
            () -> assertNotNull(run.triggeredAt),
            () -> assertSame(run.triggeredAt, run.expiryDate)
        );
    }

    @Test
    void scheduledRunsUseAsyncLaneWhileOrdinaryEventsUseDefaultLane() {
        LambdaRuntimeAssignment assignment = assignment();
        TriggerEventData input = new TriggerEventData();

        LambdaRun scheduledRun = new LambdaRun(
            assignment,
            new ScheduledEvent(assignment.locationId, assignment.lambdaAssignmentId, List.of("morning")),
            input);
        LambdaRun eventRun = new LambdaRun(
            assignment,
            new TriggerEvent(TriggerEvent.TRIGGER_LOCATION_EVENT, assignment.locationId),
            input);

        assertAll(
            () -> assertEquals(InvocationLane.ASYNC, scheduledRun.invocationLane),
            () -> assertEquals("lambda-async:1", scheduledRun.functionName),
            () -> assertEquals(InvocationLane.DEFAULT, eventRun.invocationLane),
            () -> assertEquals("lambda:1", eventRun.functionName)
        );
    }

    private static LambdaRuntimeAssignment assignment() {
        LambdaRuntimeVersion version = new LambdaRuntimeVersion();
        version.lambdaVersionId = 3;
        version.functionName = "lambda:1";
        version.asyncFunctionName = "lambda-async:1";
        version.memory = 1024;
        version.timeout = 30;

        LambdaRuntimeAssignment assignment = new LambdaRuntimeAssignment();
        assignment.lambdaAssignmentId = 7;
        assignment.lambdaId = 11;
        assignment.locationId = 13;
        assignment.version = version;
        return assignment;
    }
}
