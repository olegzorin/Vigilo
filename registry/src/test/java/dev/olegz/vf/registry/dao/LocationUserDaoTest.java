package dev.olegz.vf.registry.dao;

import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.VigiloEnvironment;
import dev.olegz.vf.registry.TestConfiguration;
import dev.olegz.vf.registry.domain.account.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * DAO tests for {@link UserLocationDao}. Each test seeds its own organization, user(s) and
 * location(s) - {@code user_locations} carries foreign keys to {@code users} and {@code locations} -
 * and runs inside a transaction that is rolled back afterwards, so no data leaks between tests or
 * into the database.
 * <p>
 * A user has at most one open assignment (NULL end_date) across all locations: insert is a no-op
 * while one is open, and delete closes it by stamping end_date.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfiguration.class)
@Transactional
@Rollback
class LocationUserDaoTest {

    @BeforeAll
    static void oneTimeSetUp() {
        VigiloEnvironment.getHomeDir();
    }

    private final UserLocationDao userLocationsDao;
    private final UserDao userDao;
    private final LocationDao locationDao;
    private final OrganizationDao organizationDao;

    @Autowired
    LocationUserDaoTest(UserLocationDao userLocationsDao, UserDao userDao,
                         LocationDao locationDao, OrganizationDao organizationDao) {
        this.userLocationsDao = userLocationsDao;
        this.userDao = userDao;
        this.locationDao = locationDao;
        this.organizationDao = organizationDao;
    }

    private int insertOrganization() {
        Organization org = new Organization();
        org.organizationName = "Org " + System.nanoTime();
        organizationDao.insertOrganization(org);
        return org.organizationId;
    }

    private User insertUser() {
        return insertUser(insertOrganization());
    }

    private User insertUser(int organizationId) {
        User user = new User();
        user.username = "user_" + System.nanoTime();
        user.password = "hash";
        user.organizationId = organizationId;
        userDao.insertUser(user);
        return user;
    }

    private int insertLocation() {
        return insertLocation(insertOrganization());
    }

    private int insertLocation(int organizationId) {
        Location loc = new Location();
        loc.locationName = "Loc " + System.nanoTime();
        loc.organizationId = organizationId;
        loc.address = new Address();
        loc.address.city = "Portland";
        loc.address.state = "OR";
        loc.address.countryCode = "US";
        loc.address.postalCode = "97201";
        loc.address.timezone = "America/Los_Angeles";
        locationDao.insertLocation(loc);
        return loc.locationId;
    }

    private LocationUser newUserLocation(int userId, int locationId) {
        LocationUser ul = new LocationUser();
        ul.userId = userId;
        ul.locationId = locationId;
        return ul;
    }

    @Test
    void insertThenGet_roundTripsAllFields() {
        User user = insertUser();
        int locationId = insertLocation();

        LocationUser ul = newUserLocation(user.userId, locationId);
        userLocationsDao.insertUserLocation(ul);
        assertNotNull(ul.startDate, "startDate should default to now on insert");

        LocationUser loaded = userLocationsDao.getUserLocation(user.userId, locationId);
        assertNotNull(loaded);
        assertEquals(user.userId, loaded.userId);
        assertEquals(locationId, loaded.locationId);
        assertEquals(ul.startDate, loaded.startDate);
        assertNull(loaded.endDate);
    }

    @Test
    void insert_keepsExplicitStartDate() {
        User user = insertUser();
        int locationId = insertLocation();

        LocationUser ul = newUserLocation(user.userId, locationId);
        Datetime startDate = Datetime.nowMinusDays(3);
        ul.startDate = startDate;

        userLocationsDao.insertUserLocation(ul);

        assertEquals(startDate, userLocationsDao.getUserLocation(user.userId, locationId).startDate);
    }

    @Test
    void getUnknownPair_returnsNull() {
        assertNull(userLocationsDao.getUserLocation(-1, -1));
    }

    @Test
    void insert_isNoop_whenOpenAssignmentExists() {
        User user = insertUser();
        int locationId = insertLocation();

        LocationUser first = newUserLocation(user.userId, locationId);
        first.startDate = Datetime.nowMinusDays(5);
        userLocationsDao.insertUserLocation(first);

        // Second insert while an assignment is open must not create a row.
        LocationUser second = newUserLocation(user.userId, locationId);
        second.startDate = Datetime.nowMinusDays(1);
        userLocationsDao.insertUserLocation(second);

        // Still exactly one open row, and it is the original.
        assertEquals(1, userLocationsDao.getUserLocationsByUser(user.userId).size());
        assertEquals(first.startDate, userLocationsDao.getUserLocation(user.userId, locationId).startDate);
    }

    @Test
    void insert_allowed_afterOpenAssignmentClosed() {
        User user = insertUser();
        int userId = user.userId;
        int locationId = insertLocation();

        LocationUser first = newUserLocation(userId, locationId);
        first.startDate = Datetime.nowMinusDays(5);
        userLocationsDao.insertUserLocation(first);

        assertTrue(userLocationsDao.deleteUserLocation(userId, locationId));

        // With no open assignment, a fresh one can be opened.
        LocationUser reopened = newUserLocation(userId, locationId);
        userLocationsDao.insertUserLocation(reopened);

        LocationUser loaded = userLocationsDao.getUserLocation(userId, locationId);
        assertNotNull(loaded);
        assertNull(loaded.endDate);
        assertEquals(reopened.startDate, loaded.startDate);
        // Only the reopened row is open; the closed one is excluded.
        assertEquals(1, userLocationsDao.getUserLocationsByUser(userId).size());
    }

    @Test
    void futureEndDate_countsAsActive() {
        User user = insertUser();
        int userId = user.userId;
        int locationId = insertLocation();

        LocationUser ul = newUserLocation(userId, locationId);
        ul.startDate = Datetime.nowMinusDays(1);
        ul.endDate = Datetime.nowPlusDays(10);
        userLocationsDao.insertUserLocation(ul);

        // A future end_date reads as active...
        LocationUser loaded = userLocationsDao.getUserLocation(userId, locationId);
        assertNotNull(loaded);
        assertEquals(ul.endDate, loaded.endDate);

        // ...so a second insert is a no-op.
        LocationUser second = newUserLocation(userId, locationId);
        second.startDate = Datetime.nowMinusDays(5);
        userLocationsDao.insertUserLocation(second);
        assertEquals(1, userLocationsDao.getUserLocationsByUser(userId).size());
    }

    @Test
    void insert_toAnotherLocationIsNoopWhileAssignmentIsActive() {
        int userId = insertUser().userId;
        int locationA = insertLocation();
        int locationB = insertLocation();

        LocationUser first = newUserLocation(userId, locationA);
        first.startDate = Datetime.nowMinusDays(1);
        assertTrue(userLocationsDao.insertUserLocation(first));

        LocationUser second = newUserLocation(userId, locationB);
        second.startDate = Datetime.nowMinusDays(5);
        assertFalse(userLocationsDao.insertUserLocation(second));

        List<LocationUser> byUser = userLocationsDao.getUserLocationsByUser(userId);
        assertEquals(1, byUser.size());
        assertEquals(locationA, byUser.get(0).locationId);
    }

    @Test
    void getByUser_excludesClosedAssignments() {
        int userId = insertUser().userId;
        int locationId = insertLocation();

        userLocationsDao.insertUserLocation(newUserLocation(userId, locationId));
        userLocationsDao.deleteUserLocation(userId, locationId);

        assertTrue(userLocationsDao.getUserLocationsByUser(userId).isEmpty());
    }

    @Test
    void getByLocation_returnsOpenAssignments() {
        int userA = insertUser().userId;
        int userB = insertUser().userId;
        int locationId = insertLocation();

        userLocationsDao.insertUserLocation(newUserLocation(userA, locationId));
        userLocationsDao.insertUserLocation(newUserLocation(userB, locationId));

        List<LocationUser> byLocation = userLocationsDao.getUserLocationsByLocation(locationId);
        assertEquals(2, byLocation.size());
        List<User> assignedUsers = userDao.getUsersByLocation(locationId);
        assertEquals(2, assignedUsers.size());
        assertTrue(userLocationsDao.hasActiveAssignments(locationId));

        assertTrue(userLocationsDao.deleteUserLocation(userA, locationId));
        assertTrue(userLocationsDao.hasActiveAssignments(locationId));
        assertTrue(userLocationsDao.deleteUserLocation(userB, locationId));
        assertFalse(userLocationsDao.hasActiveAssignments(locationId));
        assertTrue(userDao.getUsersByLocation(locationId).isEmpty());
    }

    @Test
    void delete_closesOpenAssignment_soGetReturnsNull() {
        int userId = insertUser().userId;
        int locationId = insertLocation();

        userLocationsDao.insertUserLocation(newUserLocation(userId, locationId));

        assertTrue(userLocationsDao.deleteUserLocation(userId, locationId));
        assertNull(userLocationsDao.getUserLocation(userId, locationId));
        // A second delete finds no open row.
        assertFalse(userLocationsDao.deleteUserLocation(userId, locationId));
    }

    @Test
    void delete_unknownAssignment_returnsFalse() {
        assertFalse(userLocationsDao.deleteUserLocation(-1, -1));
    }

    @Test
    void locationsByUser_returnsCurrentAssignmentInOrganization() {
        int organizationId = insertOrganization();
        User user = insertUser(organizationId);
        int locationId = insertLocation(organizationId);
        userLocationsDao.insertUserLocation(newUserLocation(user.userId, locationId));

        Location location = locationDao.getLocationByUser(user);

        assertNotNull(location);
        assertEquals(locationId, location.locationId);
    }
}
