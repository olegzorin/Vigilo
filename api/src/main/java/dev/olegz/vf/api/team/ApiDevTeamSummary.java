package dev.olegz.vf.api.team;

import dev.olegz.vf.core.domain.lambdaversion.DevTeam;

public class ApiDevTeamSummary {
    public final int devTeamId;
    public final int ownerUserId;
    public final String name;
    public final String description;
    public final int membersCount;
    public final int lambdasCount;

    public ApiDevTeamSummary(DevTeam devTeam) {
        this.devTeamId = devTeam.devTeamId;
        this.ownerUserId = devTeam.ownerUserId;
        this.name = devTeam.name;
        this.description = devTeam.description;
        this.membersCount = devTeam.membersCount;
        this.lambdasCount = devTeam.lambdasCount;
    }
}
