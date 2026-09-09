package dev.olegz.vf.api.team;

import java.sql.Timestamp;

import dev.olegz.vf.core.domain.lambdaversion.DevTeamMember;

public class ApiDevTeamMember {
    public final int userId;
    public final Timestamp startDate;
    public final Timestamp endDate;

    public ApiDevTeamMember(DevTeamMember member) {
        this.userId = member.userId;
        this.startDate = member.startDate;
        this.endDate = member.endDate;
    }
}
