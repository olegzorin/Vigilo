package dev.olegz.vf.registry.dao.impl;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.registry.cache.CacheNames;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.dao.mapper.OrganizationMapper;
import dev.olegz.vf.registry.domain.account.Address;
import dev.olegz.vf.registry.domain.account.Organization;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository("organizationDao")
public class OrganizationDaoImpl implements OrganizationDao {
    private final OrganizationMapper mapper;

    public OrganizationDaoImpl(OrganizationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertOrganization(Organization organization) {
        if (organization.createdAt == null) {
            organization.createdAt = Datetime.now();
        }
        if (organization.address == null) {
            organization.address = new Address();
        }
        mapper.insertOrganization(organization);
    }

    @Override
    public Organization getOrganization(int organizationId) {
        return mapper.selectOrganization(organizationId);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.TRIGGER_LOCATION_METADATA, allEntries = true)
    public boolean updateOrganization(Organization organization) {
        if (organization.address == null) {
            organization.address = new Address();
        }
        return mapper.updateOrganization(organization);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.TRIGGER_LOCATION_METADATA, allEntries = true)
    public boolean deleteOrganization(int organizationId) {
        return mapper.deleteOrganization(organizationId);
    }
}
