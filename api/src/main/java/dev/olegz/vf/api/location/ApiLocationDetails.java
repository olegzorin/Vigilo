package dev.olegz.vf.api.location;

import java.util.List;
import dev.olegz.vf.api.team.ApiGrantedTeam;

import dev.olegz.vf.registry.domain.account.Location;
import dev.olegz.vf.registry.domain.account.LocationCurrentState;

public class ApiLocationDetails extends ApiLocation {
    public List<ApiGrantedTeam> grantedTeams = List.of();
    public final ApiLocationCurrentState currentState;

    public ApiLocationDetails(Location location, LocationCurrentState currentState) {
        super(location);
        this.currentState = currentState == null ? null : new ApiLocationCurrentState(currentState);
    }
}
