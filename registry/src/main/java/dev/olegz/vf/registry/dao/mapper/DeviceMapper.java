package dev.olegz.vf.registry.dao.mapper;

import java.util.List;

import dev.olegz.vf.registry.domain.device.Device;
import dev.olegz.vf.registry.domain.device.DeviceCurrentState;
import dev.olegz.vf.registry.domain.device.LocationDevice;
import org.apache.ibatis.annotations.Param;

public interface DeviceMapper {
    int insertDevice(Device device);

    Device selectDevice(
        @Param("organizationId") int organizationId,
        @Param("deviceUuid") String deviceUuid);

    DeviceCurrentState selectDeviceCurrentState(
        @Param("organizationId") int organizationId,
        @Param("deviceUuid") String deviceUuid);

    DeviceCurrentState selectDeviceCurrentStateByDeviceUuid(String deviceUuid);

    int insertDeviceCurrentState(DeviceCurrentState currentState);

    int updateDeviceCurrentState(DeviceCurrentState currentState);

    List<Device> selectDevices(
        @Param("organizationId") int organizationId,
        @Param("typeId") Integer typeId,
        @Param("locationId") Integer locationId);

    List<LocationDevice> selectLocationDevices(
        @Param("organizationId") int organizationId,
        @Param("typeId") Integer typeId,
        @Param("locationId") Integer locationId);

    int updateDevice(Device device);

    void lockDevice(String deviceUuid);

    int insertLocationDevice(LocationDevice locationDevice);

    LocationDevice selectLocationDevice(
        @Param("organizationId") int organizationId,
        @Param("deviceUuid") String deviceUuid,
        @Param("locationId") Integer locationId);

    boolean hasActiveLocationAssignments(int locationId);

    int deleteLocationDevice(
        @Param("deviceUuid") String deviceUuid,
        @Param("locationId") int locationId);
}
