package dev.olegz.vf.report.domain;

import java.sql.Timestamp;

public class ReportGroupOrganization {
    public int reportGroupId;
    public int organizationId;
    public Timestamp assignedAt;
    public String timezone;

    @Override
    public String toString() {
        return "{reportGroupId=" + reportGroupId + ", organizationId=" + organizationId +
            ", assignedAt=" + assignedAt + '}';
    }
}
