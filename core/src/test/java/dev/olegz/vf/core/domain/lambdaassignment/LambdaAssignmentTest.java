package dev.olegz.vf.core.domain.lambdaassignment;

import dev.olegz.vf.common.Datetime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LambdaAssignmentTest {

    @Test
    void endDateControlsWhetherAssignmentIsActive() {
        LambdaAssignment assignment = new LambdaAssignment(11, 21, false);

        assertTrue(assignment.checkActive());
        assertNull(assignment.endDate);

        assignment.setEnabled(false);

        assertFalse(assignment.checkActive());
        assertInstanceOf(Datetime.class, assignment.endDate);

        assignment.setEnabled(true);

        assertTrue(assignment.checkActive());
        assertNull(assignment.endDate);
    }

    @Test
    void deletedAssignmentIsInactiveRegardlessOfEndDate() {
        LambdaAssignment assignment = new LambdaAssignment(11, 21, false);
        assignment.deletedAt = Datetime.now();

        assertFalse(assignment.checkActive());
    }
}
