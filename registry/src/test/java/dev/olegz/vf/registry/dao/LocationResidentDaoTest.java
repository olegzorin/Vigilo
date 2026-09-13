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
 * DAO tests for {@link ResidentLocationDao}. Each test seeds its own organization, resident(s) and
 * location(s) - {@code resident_locations} carries foreign keys to {@code residents} and {@code locations} -
 * and runs inside a transaction that is rolled back afterwards, so no data leaks between tests or
 * into the database.
 * <p>
 * A resident has at most one open assignment (NULL end_date) across all locations: insert is a no-op
 * while one is open, and delete closes it by stamping end_date.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfiguration.class)
@Transactional
@Rollback
class LocationResidentDaoTest {

    @BeforeAll
    static void oneTimeSetUp() {
        VigiloEnvironment.getHomeDir();
    }

    private final ResidentLocationDao residentLocationsDao;
    private final ResidentDao residentDao;
    private final LocationDao locationDao;
    private final OrganizationDao organizationDao;

    @Autowired
    LocationResidentDaoTest(ResidentLocationDao residentLocationsDao, ResidentDao residentDao,
                         LocationDao locationDao, OrganizationDao organizationDao) {
        this.residentLocationsDao = residentLocationsDao;
        this.residentDao = residentDao;
        this.locationDao = locationDao;
        this.organizationDao = organizationDao;
    }

    private int insertOrganization() {
        Organization org = new Organization();
        org.organizationName = "Org " + System.nanoTime();
        organizationDao.insertOrganization(org);
        return org.organizationId;
    }

    private Resident insertResident() {
        return insertResident(insertOrganization());
    }

    private Resident insertResident(int organizationId) {
        Resident resident = new Resident();
        resident.firstName = "resident_" + System.nanoTime();
        resident.organizationId = organizationId;
        residentDao.insertResident(resident);
        return resident;
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

    private LocationResident newResidentLocation(int residentId, int locationId) {
        LocationResident ul = new LocationResident();
        ul.residentId = residentId;
        ul.locationId = locationId;
        return ul;
    }

    @Test
    void insertThenGet_roundTripsAllFields() {
        Resident resident = insertResident();
        int locationId = insertLocation();

        LocationResident ul = newResidentLocation(resident.residentId, locationId);
        residentLocationsDao.insertResidentLocation(ul);
        assertNotNull(ul.startDate, "startDate should default to now on insert");

        LocationResident loaded = residentLocationsDao.getResidentLocation(resident.residentId, locationId);
        assertNotNull(loaded);
        assertEquals(resident.residentId, loaded.residentId);
        assertEquals(locationId, loaded.locationId);
        assertEquals(ul.startDate, loaded.startDate);
        assertNull(loaded.endDate);
    }

    @Test
    void insert_keepsExplicitStartDate() {
        Resident resident = insertResident();
        int locationId = insertLocation();

        LocationResident ul = newResidentLocation(resident.residentId, locationId);
        Datetime startDate = Datetime.nowMinusDays(3);
        ul.startDate = startDate;

        residentLocationsDao.insertResidentLocation(ul);

        assertEquals(startDate, residentLocationsDao.getResidentLocation(resident.residentId, locationId).startDate);
    }

    @Test
    void getUnknownPair_returnsNull() {
        assertNull(residentLocationsDao.getResidentLocation(-1, -1));
    }

    @Test
    void insert_isNoop_whenOpenAssignmentExists() {
        Resident resident = insertResident();
        int locationId = insertLocation();

        LocationResident first = newResidentLocation(resident.residentId, locationId);
        first.startDate = Datetime.nowMinusDays(5);
        residentLocationsDao.insertResidentLocation(first);

        // Second insert while an assignment is open must not create a row.
        LocationResident second = newResidentLocation(resident.residentId, locationId);
        second.startDate = Datetime.nowMinusDays(1);
        residentLocationsDao.insertResidentLocation(second);

        // Still exactly one open row, and it is the original.
        assertEquals(1, residentLocationsDao.getResidentLocationsByResident(resident.residentId).size());
        assertEquals(first.startDate, residentLocationsDao.getResidentLocation(resident.residentId, locationId).startDate);
    }

    @Test
    void insert_allowed_afterOpenAssignmentClosed() {
        Resident resident = insertResident();
        int residentId = resident.residentId;
        int locationId = insertLocation();

        LocationResident first = newResidentLocation(residentId, locationId);
        first.startDate = Datetime.nowMinusDays(5);
        residentLocationsDao.insertResidentLocation(first);

        assertTrue(residentLocationsDao.deleteResidentLocation(residentId, locationId));

        // With no open assignment, a fresh one can be opened.
        LocationResident reopened = newResidentLocation(residentId, locationId);
        residentLocationsDao.insertResidentLocation(reopened);

        LocationResident loaded = residentLocationsDao.getResidentLocation(residentId, locationId);
        assertNotNull(loaded);
        assertNull(loaded.endDate);
        assertEquals(reopened.startDate, loaded.startDate);
        // Only the reopened row is open; the closed one is excluded.
        assertEquals(1, residentLocationsDao.getResidentLocationsByResident(residentId).size());
    }

    @Test
    void futureEndDate_countsAsActive() {
        Resident resident = insertResident();
        int residentId = resident.residentId;
        int locationId = insertLocation();

        LocationResident ul = newResidentLocation(residentId, locationId);
        ul.startDate = Datetime.nowMinusDays(1);
        ul.endDate = Datetime.nowPlusDays(10);
        residentLocationsDao.insertResidentLocation(ul);

        // A future end_date reads as active...
        LocationResident loaded = residentLocationsDao.getResidentLocation(residentId, locationId);
        assertNotNull(loaded);
        assertEquals(ul.endDate, loaded.endDate);

        // ...so a second insert is a no-op.
        LocationResident second = newResidentLocation(residentId, locationId);
        second.startDate = Datetime.nowMinusDays(5);
        residentLocationsDao.insertResidentLocation(second);
        assertEquals(1, residentLocationsDao.getResidentLocationsByResident(residentId).size());
    }

    @Test
    void insert_toAnotherLocationIsNoopWhileAssignmentIsActive() {
        int residentId = insertResident().residentId;
        int locationA = insertLocation();
        int locationB = insertLocation();

        LocationResident first = newResidentLocation(residentId, locationA);
        first.startDate = Datetime.nowMinusDays(1);
        assertTrue(residentLocationsDao.insertResidentLocation(first));

        LocationResident second = newResidentLocation(residentId, locationB);
        second.startDate = Datetime.nowMinusDays(5);
        assertFalse(residentLocationsDao.insertResidentLocation(second));

        List<LocationResident> byResident = residentLocationsDao.getResidentLocationsByResident(residentId);
        assertEquals(1, byResident.size());
        assertEquals(locationA, byResident.get(0).locationId);
    }

    @Test
    void getByResident_excludesClosedAssignments() {
        int residentId = insertResident().residentId;
        int locationId = insertLocation();

        residentLocationsDao.insertResidentLocation(newResidentLocation(residentId, locationId));
        residentLocationsDao.deleteResidentLocation(residentId, locationId);

        assertTrue(residentLocationsDao.getResidentLocationsByResident(residentId).isEmpty());
    }

    @Test
    void getByLocation_returnsOpenAssignments() {
        int residentA = insertResident().residentId;
        int residentB = insertResident().residentId;
        int locationId = insertLocation();

        residentLocationsDao.insertResidentLocation(newResidentLocation(residentA, locationId));
        residentLocationsDao.insertResidentLocation(newResidentLocation(residentB, locationId));

        List<LocationResident> byLocation = residentLocationsDao.getResidentLocationsByLocation(locationId);
        assertEquals(2, byLocation.size());
        List<Resident> assignedResidents = residentDao.getResidentsByLocation(locationId);
        assertEquals(2, assignedResidents.size());
        assertTrue(residentLocationsDao.hasActiveAssignments(locationId));

        assertTrue(residentLocationsDao.deleteResidentLocation(residentA, locationId));
        assertTrue(residentLocationsDao.hasActiveAssignments(locationId));
        assertTrue(residentLocationsDao.deleteResidentLocation(residentB, locationId));
        assertFalse(residentLocationsDao.hasActiveAssignments(locationId));
        assertTrue(residentDao.getResidentsByLocation(locationId).isEmpty());
    }

    @Test
    void delete_closesOpenAssignment_soGetReturnsNull() {
        int residentId = insertResident().residentId;
        int locationId = insertLocation();

        residentLocationsDao.insertResidentLocation(newResidentLocation(residentId, locationId));

        assertTrue(residentLocationsDao.deleteResidentLocation(residentId, locationId));
        assertNull(residentLocationsDao.getResidentLocation(residentId, locationId));
        // A second delete finds no open row.
        assertFalse(residentLocationsDao.deleteResidentLocation(residentId, locationId));
    }

    @Test
    void delete_unknownAssignment_returnsFalse() {
        assertFalse(residentLocationsDao.deleteResidentLocation(-1, -1));
    }

    @Test
    void locationsByResident_returnsCurrentAssignmentInOrganization() {
        int organizationId = insertOrganization();
        Resident resident = insertResident(organizationId);
        int locationId = insertLocation(organizationId);
        residentLocationsDao.insertResidentLocation(newResidentLocation(resident.residentId, locationId));

        LocationResident location = residentLocationsDao.getResidentLocation(resident.residentId, locationId);

        assertNotNull(location);
        assertEquals(locationId, location.locationId);
    }
}
