package dev.olegz.vf.api.device;

import dev.olegz.vf.registry.domain.device.Device;
import dev.olegz.vf.registry.domain.device.DeviceCurrentState;
import dev.olegz.vf.registry.domain.device.LocationDevice;

public class ApiDeviceDetails extends ApiDevice {
    public final ApiDeviceCurrentState currentState;

    public ApiDeviceDetails(
        Device device,
        LocationDevice assignment,
        DeviceCurrentState currentState)
    {
        super(device, assignment, true);
        this.currentState = currentState == null ? null : new ApiDeviceCurrentState(currentState);
    }
}
