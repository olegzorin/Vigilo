package dev.olegz.vf.registry.dao;

import dev.olegz.vf.registry.domain.account.Organization;

public interface OrganizationDao {

    /**
     * Insert a new organization. On return {@link Organization#organizationId} holds the generated id.
     * If {@link Organization#createdAt} is not set it defaults to the current time.
     */
    void insertOrganization(Organization organization);

    /**
     * @return the organization with the given id, or {@code null} if none exists or it was deleted.
     */
    Organization getOrganization(int organizationId);

    /**
     * Update the mutable fields of a non-deleted organization.
     * @return {@code true} if a row was updated.
     */
    boolean updateOrganization(Organization organization);

    /**
     * Permanently mark an organization deleted by setting its deletion timestamp.
     * @return {@code true} if a non-deleted organization was deleted.
     */
    boolean deleteOrganization(int organizationId);

}
