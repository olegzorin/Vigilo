package dev.olegz.vf.api.organization;

import dev.olegz.vf.api.account.ApiAddress;
import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.registry.domain.account.Organization;

public class ApiOrganization {
    public final int organizationId;
    public final String organizationName;
    public final Datetime createdAt;
    public final Datetime deletedAt;
    public final Integer parentId;
    public final Integer adminUserId;
    public final ApiAddress address;

    public ApiOrganization(Organization organization) {
        this.organizationId = organization.organizationId;
        this.organizationName = organization.organizationName;
        this.createdAt = organization.createdAt;
        this.deletedAt = organization.deletedAt;
        this.parentId = organization.parentId;
        this.adminUserId = organization.adminUserId;
        this.address = new ApiAddress(organization.address);
    }
}
