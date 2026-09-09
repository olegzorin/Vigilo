package dev.olegz.vf.api.device;

import dev.olegz.vf.registry.domain.device.Device;
import dev.olegz.vf.registry.domain.device.LocationDevice;

public class ApiDevice {
    public final String deviceUuid;
    public final int organizationId;
    public final int typeId;
    public final String deviceName;
    public final String serialNumber;
    public final String model;
    public final String vendor;
    public final String manufacturer;
    public final ApiDeviceAssignment assignment;

    public ApiDevice(Device device, LocationDevice assignment) {
        this(device, assignment, false);
    }

    protected ApiDevice(Device device, LocationDevice assignment, boolean includeLocation) {
        this.deviceUuid = device.deviceUuid;
        this.organizationId = device.organizationId;
        this.typeId = device.typeId;
        this.deviceName = device.deviceName;
        this.serialNumber = device.serialNumber;
        this.model = device.model;
        this.vendor = device.vendor;
        this.manufacturer = device.manufacturer;
        this.assignment = assignment == null
            ? null
            : includeLocation ? new ApiDeviceLocation(assignment) : new ApiDeviceAssignment(assignment);
    }
}
