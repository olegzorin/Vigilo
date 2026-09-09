package dev.olegz.vf.registry.dao;

import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.VigiloEnvironment;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.registry.TestConfiguration;
import dev.olegz.vf.registry.domain.account.Address;
import dev.olegz.vf.registry.domain.account.Location;
import dev.olegz.vf.registry.domain.account.LocationCurrentState;
import dev.olegz.vf.registry.domain.account.Organization;
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
 * DAO tests for {@link LocationDao}. Each test seeds its own organization (locations carry a
 * foreign key to {@code organizations}) and locations, and runs inside a transaction that is
 * rolled back afterwards, so no data leaks between tests or into the database.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfiguration.class)
@Transactional
@Rollback
class LocationDaoTest {

    @BeforeAll
    static void oneTimeSetUp() {
        VigiloEnvironment.getHomeDir();
    }

    private final LocationDao locationDao;
    private final OrganizationDao organizationDao;

    @Autowired
    LocationDaoTest(LocationDao locationDao, OrganizationDao organizationDao) {
        this.locationDao = locationDao;
        this.organizationDao = organizationDao;
    }

    private int insertOrganization() {
        Organization org = new Organization();
        org.organizationName = "Org " + System.nanoTime();
        organizationDao.insertOrganization(org);
        return org.organizationId;
    }

    private static Location newLocation(int organizationId, String name) {
        Location loc = new Location();
        loc.locationName = name;
        loc.organizationId = organizationId;
        loc.address = new Address();
        loc.address.addrStreet1 = "1 Elm St";
        loc.address.addrStreet2 = "Floor 3";
        loc.address.city = "Portland";
        loc.address.state = "OR";
        loc.address.countryCode = "US";
        loc.address.postalCode = "97201";
        loc.address.timezone = "America/Los_Angeles";
        return loc;
    }

    @Test
    void insertThenGet_roundTripsAllFields() {
        int organizationId = insertOrganization();
        Location loc = newLocation(organizationId, "HQ");

        locationDao.insertLocation(loc);

        assertTrue(loc.locationId > 0);
        assertNotNull(loc.createdAt, "createdAt should default to now on insert");

        Location loaded =  locationDao.getOrganizationLocation(organizationId, loc.locationId);
        assertNotNull(loaded);
        assertEquals(loc.locationId, loaded.locationId);
        assertEquals("HQ", loaded.locationName);
        assertEquals(organizationId, loaded.organizationId);
        assertNotNull(loaded.createdAt);
        assertNull(loaded.deletedAt);
        assertNotNull(loaded.address);
        assertEquals("1 Elm St", loaded.address.addrStreet1);
        assertEquals("Floor 3", loaded.address.addrStreet2);
        assertEquals("Portland", loaded.address.city);
        assertEquals("OR", loaded.address.state);
        assertEquals("US", loaded.address.countryCode);
        assertEquals("97201", loaded.address.postalCode);
        assertEquals("America/Los_Angeles", loaded.address.timezone);
    }

    @Test
    void saveThenGetCurrentState_insertsAndUpdatesWithinOrganization() {
        int organizationId = insertOrganization();
        int otherOrganizationId = insertOrganization();
        Location location = newLocation(organizationId, "State location");
        locationDao.insertLocation(location);

        LocationCurrentState currentState = new LocationCurrentState();
        currentState.locationId = location.locationId;
        currentState.state = "HOME";

        LocationCurrentState previousState = locationDao.saveLocationCurrentState(currentState);

        assertNull(previousState);
        assertNotNull(currentState.stateDate);
        LocationCurrentState inserted =
            locationDao.getLocationCurrentState(organizationId, location.locationId);
        assertEquals(location.locationId, inserted.locationId);
        assertEquals("HOME", inserted.state);
        assertEquals(currentState.stateDate, inserted.stateDate);
        assertNull(locationDao.getLocationCurrentState(otherOrganizationId, location.locationId));

        Datetime updatedDate = Datetime.now();
        currentState.state = "AWAY";
        currentState.stateDate = updatedDate;
        previousState = locationDao.saveLocationCurrentState(currentState);

        assertEquals(location.locationId, previousState.locationId);
        assertEquals("HOME", previousState.state);
        assertEquals(inserted.stateDate, previousState.stateDate);
        LocationCurrentState updated =
            locationDao.getLocationCurrentState(organizationId, location.locationId);
        assertEquals("AWAY", updated.state);
        assertEquals(updatedDate, updated.stateDate);
    }

    @Test
    void update_changesMutableFields() {
        int organizationId = insertOrganization();
        Location loc = newLocation(organizationId, "Before");
        locationDao.insertLocation(loc);

        loc.locationName = "After";
        loc.address.city = "Seattle";
        loc.address.postalCode = "98101";

        assertTrue(locationDao.updateLocation(loc));

        Location loaded =  locationDao.getOrganizationLocation(organizationId, loc.locationId);
        assertEquals("After", loaded.locationName);
        assertEquals("Seattle", loaded.address.city);
        assertEquals("98101", loaded.address.postalCode);
    }

    @Test
    void delete_marksEntityDeleted_soGetReturnsNull() {
        int organizationId = insertOrganization();
        Location loc = newLocation(organizationId, "Temp");
        locationDao.insertLocation(loc);

        assertTrue(locationDao.deleteLocation(loc.locationId));
        assertNull( locationDao.getOrganizationLocation(organizationId, loc.locationId));
        // Deletion is final.
        assertFalse(locationDao.deleteLocation(loc.locationId));
    }

    @Test
    void get_excludesLocationWithFutureDeletionTimestamp() {
        int organizationId = insertOrganization();
        Location loc = newLocation(organizationId, "Deleted");
        loc.deletedAt = Datetime.nowPlusDays(1);
        locationDao.insertLocation(loc);

        assertNull(locationDao.getOrganizationLocation(organizationId, loc.locationId));
    }

    @Test
    void update_withDeletedAtPermanentlyDeletesLocation() {
        int organizationId = insertOrganization();
        Location loc = newLocation(organizationId, "Deleted by update");
        locationDao.insertLocation(loc);

        loc.deletedAt = Datetime.now();

        assertTrue(locationDao.updateLocation(loc));
        assertNull(locationDao.getOrganizationLocation(organizationId, loc.locationId));
        assertFalse(locationDao.updateLocation(loc));
    }

    @Test
    void saveCurrentState_rejectsDeletedLocation() {
        int organizationId = insertOrganization();
        Location loc = newLocation(organizationId, "Deleted state location");
        locationDao.insertLocation(loc);
        assertTrue(locationDao.deleteLocation(loc.locationId));
        LocationCurrentState state = new LocationCurrentState();
        state.locationId = loc.locationId;
        state.state = "HOME";

        assertThrows(
            ObjectNotFoundException.class,
            () -> locationDao.saveLocationCurrentState(state));
    }

    @Test
    void getByOrganization_returnsOnlyItsNonDeletedLocations() {
        int organizationId = insertOrganization();
        Location second = newLocation(organizationId, "B location");
        Location first = newLocation(organizationId, "A location");
        locationDao.insertLocation(second);
        locationDao.insertLocation(first);

        List<Location> locations = locationDao.getLocationsByOrganization(organizationId);

        assertEquals(2, locations.size());
        assertEquals(first.locationId, locations.get(0).locationId);
        assertEquals(second.locationId, locations.get(1).locationId);
    }
}
