package dev.olegz.vf.core.domain.lambdarun;

import dev.olegz.vf.common.Datetime;

/**
 * Active assignment with the resolved version and ownership data required by the lambda runtime.
 */
public class LambdaRuntimeAssignment {
    public int lambdaAssignmentId;
    public int lambdaId;
    public int developerTeamId;
    public LambdaRuntimeVersion version;
    public int locationId;
    public Datetime endDate;
    // Organization owning the assignment location; this is runtime context, not an assignment scope.
    public int organizationId;

    @Override
    public String toString() {
        String s = "{lambdaAssignmentId=" + lambdaAssignmentId +
            ", lambdaId=" + lambdaId +
            ", developerTeamId=" + developerTeamId +
            ", version=" + version +
            ", locationId=" + locationId +
            ", organizationId=" + organizationId +
            '}';

        return s;
    }
}
