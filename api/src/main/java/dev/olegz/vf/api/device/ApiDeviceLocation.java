package dev.olegz.vf.api.device;

import dev.olegz.vf.api.location.ApiLocation;
import dev.olegz.vf.registry.domain.device.LocationDevice;

public class ApiDeviceLocation extends ApiDeviceAssignment {
    public final ApiLocation location;

    public ApiDeviceLocation(LocationDevice assignment) {
        super(assignment);
        this.location = assignment.location == null ? null : new ApiLocation(assignment.location);
    }
}
