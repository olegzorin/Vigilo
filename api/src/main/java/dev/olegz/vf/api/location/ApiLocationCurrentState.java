package dev.olegz.vf.api.location;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.registry.domain.account.LocationCurrentState;

public class ApiLocationCurrentState {
    public final String state;
    public final Datetime stateDate;

    public ApiLocationCurrentState(LocationCurrentState currentState) {
        this.state = currentState.state;
        this.stateDate = currentState.stateDate;
    }
}
