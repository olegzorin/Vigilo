package dev.olegz.vf.api.device;

import java.util.Map;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.registry.domain.device.DeviceCurrentState;

public class ApiDeviceCurrentState {
    public final Map<String, Object> state;
    public final Datetime measuredAt;
    public final Datetime receivedAt;

    public ApiDeviceCurrentState(DeviceCurrentState currentState) {
        this.state = currentState.state;
        this.measuredAt = currentState.measuredAt;
        this.receivedAt = currentState.receivedAt;
    }
}
