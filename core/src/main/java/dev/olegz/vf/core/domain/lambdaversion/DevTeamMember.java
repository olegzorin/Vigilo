package dev.olegz.vf.core.domain.lambdaversion;

import java.sql.Timestamp;

public class DevTeamMember {
    public int userId;
    public Timestamp startDate;
    public Timestamp endDate;

    @Override
    public String toString() {
        return "{userId=" + userId + ", startDate=" + startDate +
            (endDate != null ? ", endDate=" + endDate : "") + '}';
    }
}
