package dev.olegz.vf.report.domain;

import java.sql.Timestamp;
import java.util.Map;
import java.util.Objects;

import dev.olegz.vf.common.ApplicationFailureException;

public class ReportGroupSchedule {
    public int scheduleId;
    public int reportId;
    public int reportGroupId;
    public Timestamp startDate;
    public ReportGroup group;
    public Map<String, String> parameters;
    public String timezone;
    public String schedule;
    public String sendTo;
    public Timestamp nextExecutionDate;
    public Timestamp lastExecutionDate;
    public byte organizationalType;

    @Override
    public String toString() {
        return "{scheduleId=" + scheduleId + ", reportId=" + reportId + ", reportGroupId=" + reportGroupId +
            ", parameters=" + parameters + ", startDate=" + startDate + ", schedule=" + schedule  +
            (nextExecutionDate != null ? ", nextExecutionDate=" + nextExecutionDate : "") +
            (lastExecutionDate != null ? ", lastExecutionDate=" + lastExecutionDate : "") +
            '}';
    }

    public long getMaxStartDateTime() {
        return (group == null) || (group.organization == null) || (group.organization.assignedAt == null) ?
            startDate.getTime() : Math.max(group.organization.assignedAt.getTime(), startDate.getTime());
    }

    @Override
    public boolean equals(Object o) {
        return (this == o) ||
            (o instanceof ReportGroupSchedule other) &&
                this.reportId == other.reportId &&
                this.reportGroupId == other.reportGroupId &&
                Objects.equals(this.parameters, other.parameters);
    }

    @Override
    public int hashCode() {
        throw new ApplicationFailureException("hashCode not designed for " + this.getClass().getName());
    }
}
