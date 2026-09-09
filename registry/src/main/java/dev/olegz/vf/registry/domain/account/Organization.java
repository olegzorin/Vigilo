package dev.olegz.vf.registry.domain.account;

import dev.olegz.vf.common.Datetime;

public class Organization {
    public int organizationId;
    public String organizationName;
    public Datetime createdAt;
    public Datetime deletedAt;
    public Integer parentId;
    public Integer adminUserId;
    public Address address;

}
