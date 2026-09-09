package dev.olegz.vf.api.location;

import dev.olegz.vf.api.account.ApiAddress;
import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.registry.domain.account.Location;

public class ApiLocation {
    public final int locationId;
    public final String locationName;
    public final Datetime createdAt;
    public final Datetime deletedAt;
    public final int organizationId;
    public final ApiAddress address;

    public ApiLocation(Location location) {
        this.locationId = location.locationId;
        this.locationName = location.locationName;
        this.createdAt = location.createdAt;
        this.deletedAt = location.deletedAt;
        this.organizationId = location.organizationId;
        this.address = new ApiAddress(location.address);
    }
}
