package dev.olegz.vf.core.domain.lambdarun;

import dev.olegz.vf.common.objectmap.StringMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InvocationLaneTest {
    @Test
    void runContextKeepsNumericFlowWireContract() {
        LambdaRunContext context = new LambdaRunContext();
        context.lambdaAssignmentId = 12;
        context.lane = InvocationLane.ASYNC;

        String json = StringMapper.toString(context);
        LambdaRunContext restored = StringMapper.readValue(json, LambdaRunContext.class);

        assertTrue(json.contains("\"flow\":1"));
        assertFalse(json.contains("\"lane\""));
        assertEquals(InvocationLane.ASYNC, restored.lane);
        assertEquals(InvocationLane.DEFAULT, StringMapper.readValue("{}", LambdaRunContext.class).lane);
    }
}
