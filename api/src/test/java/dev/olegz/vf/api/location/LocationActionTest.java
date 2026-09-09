package dev.olegz.vf.api.location;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.olegz.vf.api.account.ApiAddress;
import dev.olegz.vf.api.web.support.ActionContext;
import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.common.exception.OperationNotAllowedException;
import dev.olegz.vf.common.objectmap.StringMapper;
import dev.olegz.vf.registry.dao.DeviceDao;
import dev.olegz.vf.registry.dao.LocationDao;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.dao.UserLocationDao;
import dev.olegz.vf.registry.domain.account.*;
import dev.olegz.vf.registry.domain.device.Device;
import dev.olegz.vf.registry.domain.device.DeviceCurrentState;
import dev.olegz.vf.registry.domain.device.LocationDevice;
import dev.olegz.vf.registry.service.account.UserLocationAssignmentService;
import dev.olegz.vf.registry.service.account.UserService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;

class LocationActionTest {

    @Test
    void ordinaryUser_cannotListOrViewLocations() {
        TestFixture fixture = new TestFixture();
        fixture.locationDao.add(location(10, 100, "Assigned"));
        fixture.userLocationsDao.activeByUser.put(2, assignment(2, 10));

        assertThrows(AccessDeniedException.class,
            () -> fixture.action.listLocations(fixture.context(2)));
        assertThrows(AccessDeniedException.class,
            () -> fixture.action.getLocation(fixture.context(2), 10));
    }

    @Test
    void ordinaryUser_cannotCreateLocation() {
        TestFixture fixture = new TestFixture();

        assertThrows(AccessDeniedException.class,
            () -> fixture.action.createLocation(fixture.context(2), createRequest()));
        assertTrue(fixture.locationDao.locations.isEmpty());
    }

    @Test
    void admin_canCreateUpdateAndDeleteLocation() {
        TestFixture fixture = new TestFixture();

        LocationAction.Response created = fixture.action.createLocation(fixture.context(1), createRequest());
        LocationAction.UpdateLocationRequest update = updateRequest();
        LocationAction.Response updated =
            fixture.action.updateLocation(fixture.context(1), created.location.locationId, update);
        fixture.action.deleteLocation(fixture.context(1), created.location.locationId);

        assertEquals("Updated", updated.location.locationName);
        assertTrue(fixture.locationDao.deleteCalled);
    }

    @Test
    void admin_getLocationReturnsAssignedUsersAndCurrentState() {
        TestFixture fixture = new TestFixture();
        Location location = location(10, 100, "Assigned");
        location.createdAt = Datetime.now();
        fixture.locationDao.add(location);
        fixture.userService.usersByLocation.put(10, List.of(user(2, 100), user(4, 100)));
        LocationCurrentState currentState = new LocationCurrentState();
        currentState.locationId = 10;
        currentState.state = "HOME";
        currentState.stateDate = dev.olegz.vf.common.Datetime.now();
        fixture.locationDao.currentStates.put(10, currentState);

        LocationAction.LocationDetailResponse response = fixture.action.getLocation(fixture.context(1), 10);

        assertEquals(10, response.location.locationId);
        assertEquals("HOME", response.location.currentState.state);
        assertEquals(currentState.stateDate, response.location.currentState.stateDate);
        assertEquals(2, response.users.get(0).userId);
        assertEquals(4, response.users.get(1).userId);
        assertEquals(2, response.collectionTotalSize);
        JsonNode locationJson = StringMapper.valueToTree(response.location);
        assertTrue(locationJson.has("createdAt"));
        assertFalse(locationJson.has("startDate"));
        assertFalse(locationJson.has("endDate"));
        location.deletedAt = Datetime.now();
        assertTrue(StringMapper.valueToTree(new ApiLocation(location)).has("deletedAt"));

        fixture.userService.usersByLocation.clear();
        fixture.locationDao.currentStates.clear();
        LocationAction.LocationDetailResponse unassigned = fixture.action.getLocation(fixture.context(1), 10);
        assertNull(unassigned.location.currentState);
        assertTrue(unassigned.users.isEmpty());
        assertEquals(0, unassigned.collectionTotalSize);
    }

    @Test
    void admin_cannotDeleteLocationWithActiveUserAssignments() {
        TestFixture fixture = new TestFixture();
        fixture.locationDao.add(location(10, 100, "Assigned"));
        fixture.userLocationsDao.activeByUser.put(2, assignment(2, 10));

        assertThrows(OperationNotAllowedException.class,
            () -> fixture.action.deleteLocation(fixture.context(1), 10));

        assertFalse(fixture.locationDao.deleteCalled);
        assertNotNull(fixture.locationDao.getOrganizationLocation(100, 10));
        assertNotNull(fixture.userLocationsDao.getUserLocation(2, 10));
    }

    @Test
    void admin_cannotDeleteLocationWithActiveDeviceAssignments() {
        TestFixture fixture = new TestFixture();
        fixture.locationDao.add(location(10, 100, "Assigned"));
        fixture.activeDeviceAssignments = true;

        assertThrows(OperationNotAllowedException.class,
            () -> fixture.action.deleteLocation(fixture.context(1), 10));

        assertFalse(fixture.locationDao.deleteCalled);
        assertNotNull(fixture.locationDao.getOrganizationLocation(100, 10));
    }

    @Test
    void assignmentEndpointsDelegateToUserLocationAssignmentService() {
        TestFixture fixture = new TestFixture();

        LocationAction.Response assigned = fixture.action.assignUser(fixture.context(1), 10, 2);
        fixture.action.cancelAssignment(fixture.context(1), 10, 2);

        assertNotNull(assigned.assignment.startDate);
        assertEquals(1, fixture.userLocationAssignmentService.caller.userId);
        assertEquals(10, fixture.userLocationAssignmentService.locationId);
        assertEquals(2, fixture.userLocationAssignmentService.userId);
        assertTrue(fixture.userLocationAssignmentService.cancelled);
    }

    private static LocationAction.CreateLocationRequest createRequest() {
        LocationAction.CreateLocationRequest request = new LocationAction.CreateLocationRequest();
        request.locationName = "Created";
        request.organizationId = 100;
        request.address = address("Moscow");
        return request;
    }

    private static LocationAction.UpdateLocationRequest updateRequest() {
        LocationAction.UpdateLocationRequest request = new LocationAction.UpdateLocationRequest();
        request.locationName = "Updated";
        request.address = address("London");
        return request;
    }

    private static ApiAddress address(String city) {
        ApiAddress address = new ApiAddress();
        address.city = city;
        address.timezone = "UTC";
        return address;
    }

    private static Location location(int locationId, int organizationId, String name) {
        Location location = new Location();
        location.locationId = locationId;
        location.locationName = name;
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

    private static LocationUser assignment(int userId, int locationId) {
        LocationUser assignment = new LocationUser();
        assignment.userId = userId;
        assignment.locationId = locationId;
        return assignment;
    }

    private static class TestFixture {
        private final FakeOrganizationDao organizationDao = new FakeOrganizationDao();
        private final FakeLocationDao locationDao = new FakeLocationDao();
        private boolean activeDeviceAssignments;
        private final DeviceDao deviceDao = new DeviceDao() {
            @Override public void insertDevice(Device device) { throw new UnsupportedOperationException(); }
            @Override public Device getDevice(int organizationId, String deviceUuid) { return null; }
            @Override public DeviceCurrentState getDeviceCurrentState(int organizationId, String deviceUuid) { return null; }
            @Override public DeviceCurrentState saveDeviceCurrentState(DeviceCurrentState currentState) { throw new UnsupportedOperationException(); }
            @Override public List<Device> getDevices(int organizationId, Integer typeId, Integer locationId) { return List.of(); }
            @Override public List<LocationDevice> getLocationDevices(int organizationId, Integer typeId, Integer locationId) { return List.of(); }
            @Override public boolean updateDevice(Device device) { return false; }
            @Override public boolean insertLocationDevice(LocationDevice locationDevice) { return false; }
            @Override public LocationDevice getLocationDevice(int organizationId, String deviceUuid, Integer locationId) { return null; }
            @Override public boolean hasActiveLocationAssignments(int locationId) { return activeDeviceAssignments; }
            @Override public boolean deleteLocationDevice(String deviceUuid, int locationId) { return false; }
        };
        private final FakeUserLocationDao userLocationsDao = new FakeUserLocationDao();
        private final FakeUserService userService = new FakeUserService();
        private final FakeUserLocationAssignmentService userLocationAssignmentService =
            new FakeUserLocationAssignmentService();
        private final LocationAction action =
            new LocationAction(
                locationDao,
                deviceDao,
                userLocationsDao,
                userService,
                userLocationAssignmentService);

        TestFixture() {
            locationDao.userLocationsDao = userLocationsDao;
            userService.users.put(1, user(1, 100));
            userService.users.put(2, user(2, 100));
        }

        ActionContext context(int userId) {
            return new ActionContext(userService.users.get(userId), organizationDao);
        }
    }

    private static class FakeOrganizationDao implements OrganizationDao {
        @Override
        public Organization getOrganization(int organizationId) {
            Organization organization = new Organization();
            organization.organizationId = organizationId;
            organization.adminUserId = organizationId == 100 ? 1 : null;
            return organization;
        }

        @Override public void insertOrganization(Organization organization) { throw new UnsupportedOperationException(); }
        @Override public boolean updateOrganization(Organization organization) { throw new UnsupportedOperationException(); }
        @Override public boolean deleteOrganization(int organizationId) { throw new UnsupportedOperationException(); }
    }

    private static class FakeLocationDao implements LocationDao {
        private final Map<Integer, Location> locations = new HashMap<>();
        private final Map<Integer, LocationCurrentState> currentStates = new HashMap<>();
        private FakeUserLocationDao userLocationsDao;
        private int nextId = 1000;
        private boolean deleteCalled;

        void add(Location location) {
            locations.put(location.locationId, location);
        }

        @Override
        public void insertLocation(Location location) {
            location.locationId = nextId++;
            add(location);
        }

        @Override
        public Location getOrganizationLocation(int organizationId, int locationId) {
            return locations.get(locationId);
        }

        @Override
        public LocationCurrentState getLocationCurrentState(int organizationId, int locationId) {
            Location location = getOrganizationLocation(organizationId, locationId);
            return location == null ? null : currentStates.get(locationId);
        }

        @Override
        public LocationCurrentState saveLocationCurrentState(LocationCurrentState currentState) {
            return currentStates.put(currentState.locationId, currentState);
        }

        @Override
        public List<Location> getLocationsByOrganization(int organizationId) {
            List<Location> result = new ArrayList<>();
            for (Location location : locations.values()) {
                if (location.organizationId == organizationId) result.add(location);
            }
            return result;
        }

        @Override
        public Location getLocationByUser(User user) {
            LocationUser assignment = userLocationsDao.activeByUser.get(user.userId);
            if (assignment != null) {
                Location location = locations.get(assignment.locationId);
                if (location != null && location.organizationId == user.organizationId) return location;
            }
            return null;
        }

        @Override
        public boolean updateLocation(Location location) {
            return locations.replace(location.locationId, location) != null;
        }

        @Override
        public boolean deleteLocation(int locationId) {
            deleteCalled = true;
            return locations.remove(locationId) != null;
        }
    }

    private static class FakeUserLocationDao implements UserLocationDao {
        private final Map<Integer, LocationUser> activeByUser = new HashMap<>();

        @Override
        public boolean insertUserLocation(LocationUser assignment) {
            if (activeByUser.containsKey(assignment.userId)) return false;
            assignment.startDate = dev.olegz.vf.common.Datetime.now();
            activeByUser.put(assignment.userId, assignment);
            return true;
        }

        @Override
        public LocationUser getUserLocation(int userId, int locationId) {
            LocationUser assignment = activeByUser.get(userId);
            return assignment != null && assignment.locationId == locationId ? assignment : null;
        }

        @Override
        public List<LocationUser> getUserLocationsByUser(int userId) {
            LocationUser assignment = activeByUser.get(userId);
            return assignment == null ? List.of() : List.of(assignment);
        }

        @Override
        public List<LocationUser> getUserLocationsByLocation(int locationId) {
            List<LocationUser> result = new ArrayList<>();
            for (LocationUser assignment : activeByUser.values()) {
                if (assignment.locationId == locationId) result.add(assignment);
            }
            return result;
        }

        @Override
        public boolean hasActiveAssignments(int locationId) {
            return !getUserLocationsByLocation(locationId).isEmpty();
        }

        @Override
        public boolean deleteUserLocation(int userId, int locationId) {
            LocationUser assignment = getUserLocation(userId, locationId);
            if (assignment == null) return false;
            activeByUser.remove(userId);
            return true;
        }

    }

    private static class FakeUserService implements UserService {
        private final Map<Integer, User> users = new HashMap<>();
        private final Map<Integer, List<User>> usersByLocation = new HashMap<>();

        @Override public User getUser(int userId) { return users.get(userId); }
        @Override public List<User> getUsers(Integer organizationId, Integer userId, String firstName, String lastName, String email) { throw new UnsupportedOperationException(); }
        @Override public List<User> getUsersByLocation(int locationId) { return usersByLocation.getOrDefault(locationId, List.of()); }
        @Override public void createUser(User user, String rawPassword) { throw new UnsupportedOperationException(); }
        @Override public User updateProfile(User user) { throw new UnsupportedOperationException(); }
        @Override public boolean changePassword(int userId, String rawPassword) { throw new UnsupportedOperationException(); }
        @Override public User authenticate(String username, String rawPassword) { throw new UnsupportedOperationException(); }
    }

    private static class FakeUserLocationAssignmentService implements UserLocationAssignmentService {
        private User caller;
        private int locationId;
        private int userId;
        private boolean cancelled;

        @Override
        public LocationUser assignUser(User caller, int locationId, int userId) {
            this.caller = caller;
            this.locationId = locationId;
            this.userId = userId;
            LocationUser assignment = assignment(userId, locationId);
            assignment.startDate = dev.olegz.vf.common.Datetime.now();
            return assignment;
        }

        @Override
        public void cancelAssignment(User caller, int locationId, int userId) {
            this.caller = caller;
            this.locationId = locationId;
            this.userId = userId;
            cancelled = true;
        }
    }
}
