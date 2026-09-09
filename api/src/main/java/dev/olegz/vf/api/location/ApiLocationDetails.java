package dev.olegz.vf.api.location;

import dev.olegz.vf.registry.domain.account.Location;
import dev.olegz.vf.registry.domain.account.LocationCurrentState;

public class ApiLocationDetails extends ApiLocation {
    public final ApiLocationCurrentState currentState;

    public ApiLocationDetails(Location location, LocationCurrentState currentState) {
        super(location);
        this.currentState = currentState == null ? null : new ApiLocationCurrentState(currentState);
    }
}
