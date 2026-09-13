package dev.olegz.vf.api.team;

import dev.olegz.vf.api.location.ApiLocation;

import java.util.List;

import dev.olegz.vf.api.lambda.ApiLambda;
import dev.olegz.vf.common.util.CollectionOps;
import dev.olegz.vf.core.domain.lambdaversion.DevTeam;

public class ApiDevTeamDetails {
    public final int organizationId;
    public final int testingLocationId;
    public final int devTeamId;
    public final int ownerUserId;
    public final String name;
    public final String description;
    public final List<ApiLocation> testingLocations;
    public List<ApiDevTeamMember> members;
    public List<ApiLambda> lambdas;

    public ApiDevTeamDetails(DevTeam devTeam) {
        this.testingLocations = devTeam.testingLocations == null ? List.of()
            : CollectionOps.map(devTeam.testingLocations, ApiLocation::new);
        this.organizationId = devTeam.organizationId;
        this.testingLocationId = devTeam.testingLocationId;
        this.devTeamId = devTeam.devTeamId;
        this.ownerUserId = devTeam.ownerUserId;
        this.name = devTeam.name;
        this.description = devTeam.description;
        this.members = CollectionOps.map(devTeam.members, ApiDevTeamMember::new);
        this.lambdas = CollectionOps.map(devTeam.lambdas, ApiLambda::new);
    }
}
