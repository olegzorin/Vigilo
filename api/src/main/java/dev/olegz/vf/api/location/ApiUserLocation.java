package dev.olegz.vf.api.location;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.registry.domain.account.LocationUser;

public class ApiUserLocation {
    public final int userId;
    public final int locationId;
    public final Datetime startDate;
    public final Datetime endDate;

    public ApiUserLocation(LocationUser locationUser) {
        this.userId = locationUser.userId;
        this.locationId = locationUser.locationId;
        this.startDate = locationUser.startDate;
        this.endDate = locationUser.endDate;
    }
}
