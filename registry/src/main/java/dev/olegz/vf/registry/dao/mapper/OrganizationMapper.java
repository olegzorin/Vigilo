package dev.olegz.vf.registry.dao.mapper;

import dev.olegz.vf.registry.domain.account.Organization;

public interface OrganizationMapper {

    void insertOrganization(Organization organization);

    Organization selectOrganization(int organizationId);

    boolean updateOrganization(Organization organization);

    boolean deleteOrganization(int organizationId);

}
