package dev.olegz.vf.api.account;

import java.util.List;

import dev.olegz.vf.api.web.support.ActionContext;
import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.objectmap.StringMapper;
import dev.olegz.vf.registry.dao.LocationDao;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.domain.account.*;
import dev.olegz.vf.registry.service.account.UserKeyService;
import dev.olegz.vf.registry.service.account.UserService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;

class UserActionTest {

    @Test
    void getUsers_adminSearchesOwnOrganization() {
        RecordingUserService userService = new RecordingUserService();
        UserAction action = new UserAction(userService, new RecordingUserKeyService(), new FakeLocationDao());
        User admin = user(42, 7);

        UserAction.UsersResponse response = action.getUsers(
            context(admin, true), " Ada ", "Love", "example.com");

        assertEquals(7, userService.organizationId);
        assertNull(userService.userId);
        assertEquals(" Ada ", userService.firstName);
        assertEquals("Love", userService.lastName);
        assertEquals("example.com", userService.email);
        assertEquals(1, response.users.size());
        assertEquals(1, response.collectionTotalSize);
        JsonNode userJson = StringMapper.valueToTree(response.users.getFirst());
        assertTrue(userJson.has("createdAt"));
        assertFalse(userJson.has("startDate"));
        assertFalse(userJson.has("endDate"));
        User deleted = user(100, 7);
        deleted.deletedAt = Datetime.now();
        assertTrue(StringMapper.valueToTree(new ApiUser(deleted)).has("deletedAt"));
    }

    @Test
    void getUsers_ordinaryUserSearchesOnlyOwnAccount() {
        RecordingUserService userService = new RecordingUserService();
        UserAction action = new UserAction(userService, new RecordingUserKeyService(), new FakeLocationDao());
        User caller = user(42, 7);

        action.getUsers(context(caller, false), null, null, null);

        assertNull(userService.organizationId);
        assertEquals(42, userService.userId);
    }

    @Test
    void authenticate_returnsUserAndApiKey() {
        RecordingUserService userService = new RecordingUserService();
        RecordingUserKeyService userKeyService = new RecordingUserKeyService();
        FakeLocationDao locationDao = new FakeLocationDao();
        locationDao.assignedLocation = location(10, 7);
        UserAction action = new UserAction(userService, userKeyService, locationDao);
        UserAction.AuthenticateRequest request = new UserAction.AuthenticateRequest();
        request.username = "alice";
        request.password = "password";

        UserAction.AuthenticationResponse response = action.authenticate(request);

        assertEquals("alice", userService.username);
        assertEquals("password", userService.password);
        assertEquals(42, response.user.userId);
        assertEquals("alice", response.user.username);
        assertEquals(10, response.location.locationId);
        assertEquals(42, userKeyService.userId);
        assertEquals("api-key", response.apiKey);
    }

    private static final class RecordingUserService implements UserService {
        private String username;
        private String password;
        private Integer organizationId;
        private Integer userId;
        private String firstName;
        private String lastName;
        private String email;

        @Override
        public User authenticate(String username, String rawPassword) {
            this.username = username;
            password = rawPassword;
            User user = new User();
            user.userId = 42;
            user.username = username;
            user.organizationId = 7;
            return user;
        }

        @Override
        public void createUser(User user, String rawPassword) {
            throw new UnsupportedOperationException();
        }

        @Override
        public User getUser(int userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<User> getUsers(
            Integer organizationId,
            Integer userId,
            String firstName,
            String lastName,
            String email)
        {
            this.organizationId = organizationId;
            this.userId = userId;
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            return List.of(user(99, 7));
        }

        @Override
        public List<User> getUsersByLocation(int locationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public User updateProfile(User user) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean changePassword(int userId, String rawPassword) {
            throw new UnsupportedOperationException();
        }
    }

    private static User user(int userId, int organizationId) {
        User user = new User();
        user.userId = userId;
        user.organizationId = organizationId;
        user.username = "user-" + userId;
        user.createdAt = Datetime.now();
        return user;
    }

    private static Location location(int locationId, int organizationId) {
        Location location = new Location();
        location.locationId = locationId;
        location.organizationId = organizationId;
        location.address = new Address();
        return location;
    }

    private static final class FakeLocationDao implements LocationDao {
        private Location assignedLocation;

        @Override
        public Location getLocationByUser(User user) {
            return assignedLocation != null && assignedLocation.organizationId == user.organizationId
                ? assignedLocation
                : null;
        }

        @Override public void insertLocation(Location location) { throw new UnsupportedOperationException(); }
        @Override public Location getOrganizationLocation(int organizationId, int locationId) { throw new UnsupportedOperationException(); }
        @Override public LocationCurrentState getLocationCurrentState(int organizationId, int locationId) { throw new UnsupportedOperationException(); }
        @Override public LocationCurrentState saveLocationCurrentState(LocationCurrentState currentState) { throw new UnsupportedOperationException(); }
        @Override public List<Location> getLocationsByOrganization(int organizationId) { throw new UnsupportedOperationException(); }
        @Override public boolean updateLocation(Location location) { throw new UnsupportedOperationException(); }
        @Override public boolean deleteLocation(int locationId) { throw new UnsupportedOperationException(); }
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

    private static final class RecordingUserKeyService implements UserKeyService {
        private int userId;

        @Override
        public String createUserKey(int userId) {
            this.userId = userId;
            return "api-key";
        }

        @Override
        public UserKeyJwtClaims parseUserKey(String key) {
            throw new UnsupportedOperationException();
        }
    }
}
