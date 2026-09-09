package dev.olegz.vf.report.rest;

import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.domain.account.Organization;
import dev.olegz.vf.registry.domain.account.User;

public final class ReportActionContext {
    private final User user;
    private final OrganizationDao organizationDao;

    public ReportActionContext(User user, OrganizationDao organizationDao) {
        this.user = user;
        this.organizationDao = organizationDao;
    }

    public User user() {
        if (user != null) return user;
        throw new AccessDeniedException("Request related user not found");
    }

    public void requireSameOrganization(int organizationId) {
        if (user().organizationId != organizationId) {
            throw new AccessDeniedException("Access to organization " + organizationId + " denied");
        }
    }

    public void requireAdmin() {
        Organization organization = organizationDao.getOrganization(user().organizationId);
        if (organization == null || organization.adminUserId == null || organization.adminUserId != user().userId) {
            throw new AccessDeniedException("Administrator privileges required");
        }
    }

    public void requireAdminOfOrganizationOrAncestor(int organizationId) {
        Organization organization = organizationDao.getOrganization(organizationId);
        while (organization != null) {
            if (organization.adminUserId != null && organization.adminUserId == user().userId) return;
            organization = organization.parentId == null ? null : organizationDao.getOrganization(organization.parentId);
        }
        throw new AccessDeniedException("Administrator privileges required for organization " + organizationId);
    }
}
