package dev.olegz.vf.api.device;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.registry.domain.device.LocationDevice;

public class ApiDeviceAssignment {
    public final int locationId;
    public final Datetime startDate;
    public final Datetime endDate;

    public ApiDeviceAssignment(LocationDevice assignment) {
        this.locationId = assignment.locationId;
        this.startDate = assignment.startDate;
        this.endDate = assignment.endDate;
    }
}
