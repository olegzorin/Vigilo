package dev.olegz.vf.api.device;

import java.util.List;
import java.util.Map;

import dev.olegz.vf.api.web.support.ActionContext;
import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.common.objectmap.StringMapper;
import dev.olegz.vf.registry.dao.DeviceDao;
import dev.olegz.vf.registry.dao.LocationDao;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.domain.account.*;
import dev.olegz.vf.registry.domain.device.Device;
import dev.olegz.vf.registry.domain.device.DeviceCurrentState;
import dev.olegz.vf.registry.domain.device.LocationDevice;
import dev.olegz.vf.registry.service.device.DeviceLocationAssignmentService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;

class DeviceActionTest {

    @Test
    void adminListsOrganizationDevicesWithFiltersAndAssignment() {
        FakeDeviceDao deviceDao = new FakeDeviceDao();
        FakeLocationDao locationDao = new FakeLocationDao();
        DeviceAction action = new DeviceAction(deviceDao, locationDao, new FakeAssignmentService());

        DeviceAction.Response response = action.listDevices(context(user(1, 7), true), 3, 10);

        assertEquals(7, deviceDao.organizationId);
        assertEquals(3, deviceDao.typeId);
        assertEquals(10, deviceDao.locationId);
        assertEquals(1, response.collectionTotalSize);
        assertEquals(10, response.devices.getFirst().assignment.locationId);
        assertEquals(ApiDeviceAssignment.class, response.devices.getFirst().assignment.getClass());
        JsonNode assignmentJson =
            StringMapper.valueToTree(response.devices.getFirst()).get("assignment");
        assertFalse(assignmentJson.has("deviceUuid"));
        assertFalse(assignmentJson.has("location"));
    }

    @Test
    void ordinaryUserListsOnlyDevicesAtCurrentLocation() {
        FakeDeviceDao deviceDao = new FakeDeviceDao();
        FakeLocationDao locationDao = new FakeLocationDao();
        DeviceAction action = new DeviceAction(deviceDao, locationDao, new FakeAssignmentService());

        action.listDevices(context(user(2, 7), false), null, null);

        assertEquals(10, deviceDao.locationId);
        assertThrows(AccessDeniedException.class,
            () -> action.listDevices(context(user(2, 7), false), null, 11));
    }

    @Test
    void adminGetsAnyOrganizationDeviceWithCurrentState() {
        FakeDeviceDao deviceDao = new FakeDeviceDao();
        DeviceAction action = new DeviceAction(deviceDao, new FakeLocationDao(), new FakeAssignmentService());

        DeviceAction.DeviceDetailResponse response =
            action.getDevice(context(user(1, 7), true), "device-2");

        assertEquals("device-2", response.device.deviceUuid);
        assertEquals("locked", response.device.currentState.state.get("lockState"));
        assertEquals(deviceDao.currentState.measuredAt, response.device.currentState.measuredAt);
        ApiDeviceLocation assignment = (ApiDeviceLocation) response.device.assignment;
        assertEquals("Home", assignment.location.locationName);
        JsonNode assignmentJson = StringMapper.valueToTree(response.device).get("assignment");
        assertFalse(assignmentJson.has("deviceUuid"));
        assertTrue(assignmentJson.has("location"));
    }

    @Test
    void ordinaryUserGetsOnlyDeviceAtCurrentLocation() {
        DeviceAction action =
            new DeviceAction(new FakeDeviceDao(), new FakeLocationDao(), new FakeAssignmentService());

        DeviceAction.DeviceDetailResponse response =
            action.getDevice(context(user(2, 7), false), "device-1");

        assertEquals(10, response.device.assignment.locationId);
        ApiDeviceLocation assignment = (ApiDeviceLocation) response.device.assignment;
        assertEquals("Home", assignment.location.locationName);
        assertEquals("locked", response.device.currentState.state.get("lockState"));
        assertThrows(AccessDeniedException.class,
            () -> action.getDevice(context(user(2, 7), false), "device-2"));
    }

    @Test
    void createAndUpdateRequireAdminAndPreserveOrganization() {
        FakeDeviceDao deviceDao = new FakeDeviceDao();
        DeviceAction action = new DeviceAction(deviceDao, new FakeLocationDao(), new FakeAssignmentService());
        DeviceAction.CreateDeviceRequest create = new DeviceAction.CreateDeviceRequest();
        create.deviceUuid = "new-device";
        create.organizationId = 7;
        create.typeId = 4;
        create.deviceName = "Created";

        DeviceAction.Response created = action.createDevice(context(user(1, 7), true), create);
        assertEquals(7, created.device.organizationId);
        assertNull(created.device.assignment);
        assertEquals("new-device", deviceDao.inserted.deviceUuid);

        DeviceAction.UpdateDeviceRequest update = new DeviceAction.UpdateDeviceRequest();
        update.typeId = 5;
        update.deviceName = "Updated";
        DeviceAction.Response updated = action.updateDevice(context(user(1, 7), true), "device-1", update);
        assertEquals(7, updated.device.organizationId);
        assertEquals(5, deviceDao.updated.typeId);

        assertThrows(AccessDeniedException.class,
            () -> action.createDevice(context(user(2, 7), false), create));
    }

    private static class FakeDeviceDao implements DeviceDao {
        private Integer organizationId;
        private Integer typeId;
        private Integer locationId;
        private Device inserted;
        private Device updated;
        private DeviceCurrentState currentState;

        @Override
        public void insertDevice(Device device) {
            inserted = device;
        }

        @Override
        public Device getDevice(int organizationId, String deviceUuid) {
            return device(deviceUuid, organizationId);
        }

        @Override
        public DeviceCurrentState getDeviceCurrentState(int organizationId, String deviceUuid) {
            currentState = new DeviceCurrentState();
            currentState.deviceUuid = deviceUuid;
            currentState.state = Map.of("lockState", "locked");
            currentState.measuredAt = Datetime.now();
            currentState.receivedAt = Datetime.now();
            return currentState;
        }

        @Override
        public DeviceCurrentState saveDeviceCurrentState(DeviceCurrentState currentState) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Device> getDevices(int organizationId, Integer typeId, Integer locationId) {
            this.organizationId = organizationId;
            this.typeId = typeId;
            this.locationId = locationId;
            return List.of(device("device-1", organizationId));
        }

        @Override
        public List<LocationDevice> getLocationDevices(
            int organizationId,
            Integer typeId,
            Integer locationId)
        {
            return List.of(assignment("device-1", 10));
        }

        @Override
        public boolean updateDevice(Device device) {
            updated = device;
            return true;
        }

        @Override public boolean insertLocationDevice(LocationDevice locationDevice) { return true; }
        @Override
        public LocationDevice getLocationDevice(int organizationId, String deviceUuid, Integer locationId) {
            if ("device-2".equals(deviceUuid) && locationId != null) return null;
            if (!"device-1".equals(deviceUuid) && !"device-2".equals(deviceUuid)) return null;
            int effectiveLocationId = locationId == null ? 10 : locationId;
            if (effectiveLocationId != 10) return null;
            LocationDevice assignment = assignment(deviceUuid, effectiveLocationId);
            assignment.device = device(deviceUuid, organizationId);
            return assignment;
        }
        @Override public boolean hasActiveLocationAssignments(int locationId) { return false; }
        @Override public boolean deleteLocationDevice(String deviceUuid, int locationId) { return true; }
    }

    private static class FakeLocationDao implements LocationDao {
        @Override
        public Location getOrganizationLocation(int organizationId, int locationId) {
            return locationId == 10 ? location(10, organizationId) : null;
        }

        @Override
        public Location getLocationByUser(User user) {
            return location(10, user.organizationId);
        }

        @Override public void insertLocation(Location location) { throw new UnsupportedOperationException(); }
        @Override public LocationCurrentState getLocationCurrentState(int organizationId, int locationId) { throw new UnsupportedOperationException(); }
        @Override public LocationCurrentState saveLocationCurrentState(LocationCurrentState currentState) { throw new UnsupportedOperationException(); }
        @Override public List<Location> getLocationsByOrganization(int organizationId) { throw new UnsupportedOperationException(); }
        @Override public boolean updateLocation(Location location) { throw new UnsupportedOperationException(); }
        @Override public boolean deleteLocation(int locationId) { throw new UnsupportedOperationException(); }
    }

    private static class FakeAssignmentService implements DeviceLocationAssignmentService {
        @Override
        public LocationDevice assignDevice(User caller, int locationId, String deviceUuid) {
            return assignment(deviceUuid, locationId);
        }

        @Override
        public void cancelAssignment(User caller, int locationId, String deviceUuid) {
        }
    }

    private static Device device(String deviceUuid, int organizationId) {
        Device device = new Device();
        device.deviceUuid = deviceUuid;
        device.organizationId = organizationId;
        device.typeId = 3;
        device.deviceName = "Device";
        return device;
    }

    private static LocationDevice assignment(String deviceUuid, int locationId) {
        LocationDevice assignment = new LocationDevice();
        assignment.deviceUuid = deviceUuid;
        assignment.locationId = locationId;
        assignment.startDate = Datetime.now();
        assignment.location = location(locationId, 7);
        return assignment;
    }

    private static Location location(int locationId, int organizationId) {
        Location location = new Location();
        location.locationId = locationId;
        location.locationName = "Home";
        location.organizationId = organizationId;
        location.address = new Address();
        return location;
    }

    private static User user(int userId, int organizationId) {
        User user = new User();
        user.userId = userId;
        user.organizationId = organizationId;
        return user;
    }

    private static ActionContext context(User user, boolean admin) {
        return new ActionContext(user, new OrganizationDao() {
            @Override
            public Organization getOrganization(int organizationId) {
                Organization organization = new Organization();
                organization.organizationId = organizationId;
                organization.adminUserId = admin ? user.userId : null;
                return organization;
            }

            @Override public void insertOrganization(Organization organization) { throw new UnsupportedOperationException(); }
            @Override public boolean updateOrganization(Organization organization) { throw new UnsupportedOperationException(); }
            @Override public boolean deleteOrganization(int organizationId) { throw new UnsupportedOperationException(); }
        });
    }
}
