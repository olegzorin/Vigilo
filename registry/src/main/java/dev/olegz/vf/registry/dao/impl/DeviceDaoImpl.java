package dev.olegz.vf.registry.dao.impl;

import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.registry.cache.CacheNames;
import dev.olegz.vf.registry.dao.DeviceDao;
import dev.olegz.vf.registry.dao.mapper.DeviceMapper;
import dev.olegz.vf.registry.domain.device.Device;
import dev.olegz.vf.registry.domain.device.DeviceCurrentState;
import dev.olegz.vf.registry.domain.device.LocationDevice;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository("devicesDao")
public class DeviceDaoImpl implements DeviceDao {
    private final DeviceMapper mapper;

    public DeviceDaoImpl(DeviceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertDevice(Device device) {
        mapper.insertDevice(device);
    }

    @Override
    public Device getDevice(int organizationId, String deviceUuid) {
        return mapper.selectDevice(organizationId, deviceUuid);
    }

    @Override
    public DeviceCurrentState getDeviceCurrentState(int organizationId, String deviceUuid) {
        return mapper.selectDeviceCurrentState(organizationId, deviceUuid);
    }

    @Override
    @Transactional
    public DeviceCurrentState saveDeviceCurrentState(DeviceCurrentState currentState) {
        mapper.lockDevice(currentState.deviceUuid);
        DeviceCurrentState previousState =
            mapper.selectDeviceCurrentStateByDeviceUuid(currentState.deviceUuid);
        if (mapper.updateDeviceCurrentState(currentState) == 0) {
            mapper.insertDeviceCurrentState(currentState);
        }
        return previousState;
    }

    @Override
    public List<Device> getDevices(int organizationId, Integer typeId, Integer locationId) {
        return mapper.selectDevices(organizationId, typeId, locationId);
    }

    @Override
    public List<LocationDevice> getLocationDevices(int organizationId, Integer typeId, Integer locationId) {
        return mapper.selectLocationDevices(organizationId, typeId, locationId);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.TRIGGER_LOCATION_DEVICE_METADATA, allEntries = true)
    public boolean updateDevice(Device device) {
        return mapper.updateDevice(device) == 1;
    }

    @Override
    @Transactional
    @CacheEvict(
        cacheNames = CacheNames.TRIGGER_LOCATION_DEVICE_METADATA,
        key = "#locationDevice.locationId")
    public boolean insertLocationDevice(LocationDevice locationDevice) {
        if (locationDevice.startDate == null) {
            locationDevice.startDate = Datetime.now();
        }
        mapper.lockDevice(locationDevice.deviceUuid);
        return mapper.insertLocationDevice(locationDevice) == 1;
    }

    @Override
    public LocationDevice getLocationDevice(int organizationId, String deviceUuid, Integer locationId) {
        return mapper.selectLocationDevice(organizationId, deviceUuid, locationId);
    }

    @Override
    public boolean hasActiveLocationAssignments(int locationId) {
        return mapper.hasActiveLocationAssignments(locationId);
    }

    @Override
    @Transactional
    @CacheEvict(
        cacheNames = CacheNames.TRIGGER_LOCATION_DEVICE_METADATA,
        key = "#locationId")
    public boolean deleteLocationDevice(String deviceUuid, int locationId) {
        return mapper.deleteLocationDevice(deviceUuid, locationId) == 1;
    }
}
