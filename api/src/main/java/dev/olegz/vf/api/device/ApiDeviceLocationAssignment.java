package dev.olegz.vf.api.device;

import dev.olegz.vf.registry.domain.device.LocationDevice;

public class ApiDeviceLocationAssignment extends ApiDeviceLocation {
    public final String deviceUuid;

    public ApiDeviceLocationAssignment(LocationDevice assignment) {
        super(assignment);
        this.deviceUuid = assignment.deviceUuid;
    }
}
