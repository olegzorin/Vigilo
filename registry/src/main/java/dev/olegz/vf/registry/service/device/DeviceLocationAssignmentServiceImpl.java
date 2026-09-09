package dev.olegz.vf.registry.service.device;

import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.common.exception.DuplicateEntityException;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.registry.dao.DeviceDao;
import dev.olegz.vf.registry.dao.LocationDao;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.domain.account.Location;
import dev.olegz.vf.registry.domain.account.Organization;
import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.registry.domain.device.Device;
import dev.olegz.vf.registry.domain.device.LocationDevice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("deviceLocationAssignmentService")
public class DeviceLocationAssignmentServiceImpl implements DeviceLocationAssignmentService {
    private final LocationDao locationDao;
    private final OrganizationDao organizationDao;
    private final DeviceDao deviceDao;

    public DeviceLocationAssignmentServiceImpl(
        LocationDao locationDao,
        OrganizationDao organizationDao,
        DeviceDao deviceDao)
    {
        this.locationDao = locationDao;
        this.organizationDao = organizationDao;
        this.deviceDao = deviceDao;
    }

    @Override
    @Transactional
    public LocationDevice assignDevice(User caller, int locationId, String deviceUuid) {
        Location location = getAdminLocation(caller, locationId);
        Device device = getOrganizationDevice(caller.organizationId, deviceUuid);

        LocationDevice assignment = new LocationDevice();
        assignment.deviceUuid = deviceUuid;
        assignment.locationId = locationId;
        assignment.device = device;
        assignment.location = location;
        if (!deviceDao.insertLocationDevice(assignment)) {
            throw new DuplicateEntityException(
                "Device " + deviceUuid + " already has an active location assignment");
        }
        return assignment;
    }

    @Override
    @Transactional
    public void cancelAssignment(User caller, int locationId, String deviceUuid) {
        getAdminLocation(caller, locationId);
        getOrganizationDevice(caller.organizationId, deviceUuid);
        if (!deviceDao.deleteLocationDevice(deviceUuid, locationId)) {
            throw new ObjectNotFoundException(
                "Active assignment of device " + deviceUuid + " to location " + locationId + " not found");
        }
    }

    private Location getAdminLocation(User caller, int locationId) {
        Location location = locationDao.getOrganizationLocation(caller.organizationId, locationId);
        if (location == null) {
            throw new ObjectNotFoundException("Location " + locationId + " not found");
        }

        Organization organization = organizationDao.getOrganization(caller.organizationId);
        if (organization == null || organization.adminUserId == null || organization.adminUserId != caller.userId) {
            throw new AccessDeniedException("Administrator privileges required");
        }
        return location;
    }

    private Device getOrganizationDevice(int organizationId, String deviceUuid) {
        Device device = deviceDao.getDevice(organizationId, deviceUuid);
        if (device == null) {
            throw new ObjectNotFoundException("Device " + deviceUuid + " not found");
        }
        return device;
    }
}
