package dev.olegz.vf.core.domain.lambdaassignment;

import java.util.Objects;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.props.DurationProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersion;

public class LambdaAssignment {
    public int lambdaAssignmentId;
    public int lambdaId;
    public int lambdaVersionId;
    public boolean testing;
    public int locationId;
    public Datetime assignmentDate;
    public Datetime endDate;
    public Datetime deletedAt;
    public Lambda lambda;
    public LambdaVersion lambdaVersion;

    private LambdaAssignment() {
    }

    public LambdaAssignment(int lambdaId, int locationId, boolean testing) {
        this.lambdaId = lambdaId;
        this.locationId = locationId;
        this.testing = testing;
        this.assignmentDate = Datetime.now();
        if (testing) endDate = computeEndDate();
    }

    @Override
    public String toString() {
        return "{lambdaAssignmentId=" + lambdaAssignmentId +
            ", lambdaId=" + lambdaId +
            ", lambdaVersionId=" + lambdaVersionId +
            ", locationId=" + locationId +
            (endDate != null ? ", endDate=" + endDate : "") +
            (deletedAt != null ? ", deletedAt=" + deletedAt : "") +
            (testing ? ", testing" : "") +
            '}';
    }

    public boolean checkActive() {
        return (deletedAt == null) && ((endDate == null) || (endDate.getTime() > System.currentTimeMillis()));
    }

    private static Datetime computeEndDate() {
        return new Datetime(System.currentTimeMillis() +
            PropertyStore.getDuration(DurationProp.DEV_LAMBDA_ASSIGNMENT_TIME_TO_RUN).toMillis());
    }

    public void setEnabled(boolean enabled) {
        endDate = enabled && testing ? computeEndDate() : enabled ? null : Datetime.now();
    }

    @Override
    public int hashCode() {
        return lambdaAssignmentId;
    }

    @Override
    public boolean equals(Object obj) {
        return (this == obj) ||
            (obj instanceof LambdaAssignment other) &&
                (this.lambdaAssignmentId == other.lambdaAssignmentId) &&
                (this.lambdaId == other.lambdaId) &&
                (this.locationId == other.locationId) &&
                Objects.equals(this.assignmentDate, other.assignmentDate);
    }
}
