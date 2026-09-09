package dev.olegz.vf.registry.service.device;

import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.registry.domain.device.LocationDevice;

public interface DeviceLocationAssignmentService {
    LocationDevice assignDevice(User caller, int locationId, String deviceUuid);

    void cancelAssignment(User caller, int locationId, String deviceUuid);
}
