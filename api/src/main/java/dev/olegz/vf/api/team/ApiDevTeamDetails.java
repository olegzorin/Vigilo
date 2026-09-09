package dev.olegz.vf.api.team;

import java.util.List;

import dev.olegz.vf.api.lambda.ApiLambda;
import dev.olegz.vf.common.util.CollectionOps;
import dev.olegz.vf.core.domain.lambdaversion.DevTeam;

public class ApiDevTeamDetails {
    public final int devTeamId;
    public final int ownerUserId;
    public final String name;
    public final String description;
    public List<ApiDevTeamMember> members;
    public List<ApiLambda> lambdas;

    public ApiDevTeamDetails(DevTeam devTeam) {
        this.devTeamId = devTeam.devTeamId;
        this.ownerUserId = devTeam.ownerUserId;
        this.name = devTeam.name;
        this.description = devTeam.description;
        this.members = CollectionOps.map(devTeam.members, ApiDevTeamMember::new);
        this.lambdas = CollectionOps.map(devTeam.lambdas, ApiLambda::new);
    }
}
