package dev.olegz.vf.registry.dao;

import java.util.List;

import dev.olegz.vf.registry.domain.device.Device;
import dev.olegz.vf.registry.domain.device.DeviceCurrentState;
import dev.olegz.vf.registry.domain.device.LocationDevice;

public interface DeviceDao {
    void insertDevice(Device device);

    Device getDevice(int organizationId, String deviceUuid);

    DeviceCurrentState getDeviceCurrentState(int organizationId, String deviceUuid);

    /**
     * Insert or update the current state for a device.
     *
     * @return the state that existed before the save, or {@code null} if this is the first state.
     */
    DeviceCurrentState saveDeviceCurrentState(DeviceCurrentState currentState);

    List<Device> getDevices(int organizationId, Integer typeId, Integer locationId);

    List<LocationDevice> getLocationDevices(int organizationId, Integer typeId, Integer locationId);

    boolean updateDevice(Device device);

    boolean insertLocationDevice(LocationDevice locationDevice);

    LocationDevice getLocationDevice(int organizationId, String deviceUuid, Integer locationId);

    boolean hasActiveLocationAssignments(int locationId);

    boolean deleteLocationDevice(String deviceUuid, int locationId);
}
