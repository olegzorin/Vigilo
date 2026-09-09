package dev.olegz.vf.api.organization;

import dev.olegz.vf.api.account.ApiAddress;
import dev.olegz.vf.api.web.support.ActionContext;
import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.common.objectmap.StringMapper;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.domain.account.Address;
import dev.olegz.vf.registry.domain.account.Organization;
import dev.olegz.vf.registry.domain.account.User;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;

class OrganizationActionTest {

    @Test
    void getOrganization_returnsCallersOrganization() {
        Organization organization = organization(10, 20);
        RecordingOrganizationDao dao = new RecordingOrganizationDao(organization);
        OrganizationAction action = new OrganizationAction(dao);

        OrganizationAction.Response response = action.getOrganization(context(10, 21, dao), 10);

        assertEquals(10, response.organization.organizationId);
        assertEquals("Organization 10", response.organization.organizationName);
        assertEquals("Europe/Moscow", response.organization.address.timezone);
        JsonNode organizationJson = StringMapper.valueToTree(response.organization);
        assertTrue(organizationJson.has("createdAt"));
        assertFalse(organizationJson.has("startDate"));
        assertFalse(organizationJson.has("endDate"));
        organizationJson = StringMapper.valueToTree(
            new ApiOrganization(deletedOrganization(10, 20)));
        assertTrue(organizationJson.has("deletedAt"));
    }

    @Test
    void getOrganization_rejectsAnotherOrganization() {
        RecordingOrganizationDao dao = new RecordingOrganizationDao(organization(10, 20));
        OrganizationAction action = new OrganizationAction(dao);

        assertThrows(AccessDeniedException.class,
            () -> action.getOrganization(context(10, 21, dao), 11));
    }

    @Test
    void updateOrganization_requiresAdmin() {
        RecordingOrganizationDao dao = new RecordingOrganizationDao(organization(10, 20));
        OrganizationAction action = new OrganizationAction(dao);

        assertThrows(AccessDeniedException.class,
            () -> action.updateOrganization(context(10, 21, dao), 10, updateRequest()));
        assertFalse(dao.updateCalled);
    }

    @Test
    void updateOrganization_updatesProfileAndPreservesManagedFields() {
        Organization organization = organization(10, 20);
        Integer parentId = organization.parentId;
        Integer adminUserId = organization.adminUserId;
        Datetime createdAt = organization.createdAt;
        RecordingOrganizationDao dao = new RecordingOrganizationDao(organization);
        OrganizationAction action = new OrganizationAction(dao);

        OrganizationAction.Response response =
            action.updateOrganization(context(10, 20, dao), 10, updateRequest());

        assertTrue(dao.updateCalled);
        assertSame(organization, dao.updatedOrganization);
        assertEquals("Updated organization", organization.organizationName);
        assertEquals("London", organization.address.city);
        assertEquals("Europe/London", organization.address.timezone);
        assertEquals(parentId, organization.parentId);
        assertEquals(adminUserId, organization.adminUserId);
        assertSame(createdAt, organization.createdAt);
        assertEquals("Updated organization", response.organization.organizationName);
    }

    @Test
    void getOrganization_reportsMissingOrganization() {
        RecordingOrganizationDao dao = new RecordingOrganizationDao(null);
        OrganizationAction action = new OrganizationAction(dao);

        assertThrows(ObjectNotFoundException.class,
            () -> action.getOrganization(context(10, 20, dao), 10));
    }

    private static ActionContext context(int organizationId, int userId, OrganizationDao dao) {
        User user = new User();
        user.userId = userId;
        user.organizationId = organizationId;
        return new ActionContext(user, dao);
    }

    private static Organization organization(int organizationId, int adminUserId) {
        Organization organization = new Organization();
        organization.organizationId = organizationId;
        organization.organizationName = "Organization " + organizationId;
        organization.parentId = 1;
        organization.adminUserId = adminUserId;
        organization.createdAt = Datetime.now();
        organization.address = new Address();
        organization.address.city = "Moscow";
        organization.address.timezone = "Europe/Moscow";
        return organization;
    }

    private static Organization deletedOrganization(int organizationId, int adminUserId) {
        Organization organization = organization(organizationId, adminUserId);
        organization.deletedAt = Datetime.now();
        return organization;
    }

    private static OrganizationAction.UpdateOrganizationRequest updateRequest() {
        OrganizationAction.UpdateOrganizationRequest request = new OrganizationAction.UpdateOrganizationRequest();
        request.organizationName = "Updated organization";
        request.address = new ApiAddress();
        request.address.city = "London";
        request.address.timezone = "Europe/London";
        return request;
    }

    private static class RecordingOrganizationDao implements OrganizationDao {
        private final Organization organization;
        private boolean updateCalled;
        private Organization updatedOrganization;

        RecordingOrganizationDao(Organization organization) {
            this.organization = organization;
        }

        @Override
        public void insertOrganization(Organization organization) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Organization getOrganization(int organizationId) {
            if (organization == null || organization.organizationId != organizationId) return null;
            return organization;
        }

        @Override
        public boolean updateOrganization(Organization organization) {
            updateCalled = true;
            updatedOrganization = organization;
            return true;
        }

        @Override
        public boolean deleteOrganization(int organizationId) {
            throw new UnsupportedOperationException();
        }
    }
}
