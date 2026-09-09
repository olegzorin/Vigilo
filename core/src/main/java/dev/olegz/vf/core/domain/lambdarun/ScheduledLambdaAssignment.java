package dev.olegz.vf.core.domain.lambdarun;

import java.sql.Timestamp;

public class ScheduledLambdaAssignment {
    public int lambdaAssignmentId;
    public int lambdaVersionId;
    public String timezone;
    public int locationId;
    public Timestamp scheduleLastDate;
    public Timestamp scheduleNextDate;
    public String schedule;

    @Override
    public String toString() {
        return "{lambdaAssignmentId=" + lambdaAssignmentId +
            (lambdaVersionId != 0 ? ", lambdaVersionId=" + lambdaVersionId : "") +
            (locationId != 0 ? ", locationId=" + locationId : "") +
            (timezone != null ? ", timezone=" + timezone : "") +
            (scheduleLastDate != null ? ", scheduleLastDate=" + scheduleLastDate : "") +
            (scheduleNextDate != null ? ", scheduleNextDate=" + scheduleNextDate : "");
    }
}
