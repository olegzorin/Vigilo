package dev.olegz.vf.registry.dao;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.VigiloEnvironment;
import dev.olegz.vf.registry.TestConfiguration;
import dev.olegz.vf.registry.domain.account.Address;
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
 * DAO tests for {@link OrganizationDao}. Each test seeds its own organizations and runs inside a
 * transaction that is rolled back afterwards, so no data leaks between tests or into the database.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfiguration.class)
@Transactional
@Rollback
class OrganizationDaoTest {

    @BeforeAll
    static void oneTimeSetUp() {
        VigiloEnvironment.getHomeDir();
    }

    private final OrganizationDao organizationDao;

    @Autowired
    OrganizationDaoTest(OrganizationDao organizationDao) {
        this.organizationDao = organizationDao;
    }

    private static Organization newOrganization(String name) {
        Organization org = new Organization();
        org.organizationName = name;
        org.address = new Address();
        org.address.addrStreet1 = "123 Main St";
        org.address.addrStreet2 = "Suite 200";
        org.address.city = "Springfield";
        org.address.state = "IL";
        org.address.countryCode = "US";
        org.address.postalCode = "62704";
        org.address.timezone = "America/Chicago";
        return org;
    }

    @Test
    void insertThenGet_roundTripsAllFields() {
        Organization org = newOrganization("Acme");

        organizationDao.insertOrganization(org);

        assertTrue(org.organizationId > 0);
        assertNotNull(org.createdAt, "createdAt should default to now on insert");

        Organization loaded = organizationDao.getOrganization(org.organizationId);
        assertNotNull(loaded);
        assertEquals(org.organizationId, loaded.organizationId);
        assertEquals("Acme", loaded.organizationName);
        assertNull(loaded.parentId);
        assertNotNull(loaded.createdAt);
        assertNull(loaded.deletedAt);
        assertNotNull(loaded.address);
        assertEquals("123 Main St", loaded.address.addrStreet1);
        assertEquals("Suite 200", loaded.address.addrStreet2);
        assertEquals("Springfield", loaded.address.city);
        assertEquals("IL", loaded.address.state);
        assertEquals("US", loaded.address.countryCode);
        assertEquals("62704", loaded.address.postalCode);
        assertEquals("America/Chicago", loaded.address.timezone);
    }

    @Test
    void insert_withParentId_persistsParentReference() {
        Organization parent = newOrganization("Parent");
        organizationDao.insertOrganization(parent);

        Organization child = newOrganization("Child");
        child.parentId = parent.organizationId;
        organizationDao.insertOrganization(child);

        Organization loaded = organizationDao.getOrganization(child.organizationId);
        assertNotNull(loaded.parentId);
        assertEquals(parent.organizationId, loaded.parentId.intValue());
    }

    @Test
    void getUnknownId_returnsNull() {
        assertNull(organizationDao.getOrganization(-1));
    }

    @Test
    void update_changesMutableFields() {
        Organization org = newOrganization("Before");
        organizationDao.insertOrganization(org);

        org.organizationName = "After";
        org.address.city = "Chicago";
        org.address.postalCode = "60601";

        assertTrue(organizationDao.updateOrganization(org));

        Organization loaded = organizationDao.getOrganization(org.organizationId);
        assertEquals("After", loaded.organizationName);
        assertEquals("Chicago", loaded.address.city);
        assertEquals("60601", loaded.address.postalCode);
    }

    @Test
    void delete_marksEntityDeleted_soGetReturnsNull() {
        Organization org = newOrganization("Temp");
        organizationDao.insertOrganization(org);

        assertTrue(organizationDao.deleteOrganization(org.organizationId));
        assertNull(organizationDao.getOrganization(org.organizationId));
        // Deletion is final.
        assertFalse(organizationDao.deleteOrganization(org.organizationId));
    }

    @Test
    void get_excludesOrganizationWithFutureDeletionTimestamp() {
        Organization org = newOrganization("Deleted");
        org.deletedAt = Datetime.nowPlusDays(1);
        organizationDao.insertOrganization(org);

        assertNull(organizationDao.getOrganization(org.organizationId));
    }

    @Test
    void update_withDeletedAtPermanentlyDeletesOrganization() {
        Organization org = newOrganization("Deleted by update");
        organizationDao.insertOrganization(org);

        org.deletedAt = Datetime.now();

        assertTrue(organizationDao.updateOrganization(org));
        assertNull(organizationDao.getOrganization(org.organizationId));
        assertFalse(organizationDao.updateOrganization(org));
    }
}
