package dev.olegz.vf.registry.domain.account;

import dev.olegz.vf.common.Datetime;

public class Location {
    public int locationId;
    public String locationName;
    public Datetime createdAt;
    public Datetime deletedAt;
    public int organizationId;
    public Address address;

    @Override
    public String toString() {
        return "locationId=" + locationId + ", locationName=" + locationName;
    }
}
