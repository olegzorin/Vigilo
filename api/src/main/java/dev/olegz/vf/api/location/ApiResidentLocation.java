package dev.olegz.vf.api.location;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.registry.domain.account.LocationResident;

public class ApiResidentLocation {
    public final int residentId;
    public final int locationId;
    public final Datetime startDate;
    public final Datetime endDate;

    public ApiResidentLocation(LocationResident locationResident) {
        this.residentId = locationResident.residentId;
        this.locationId = locationResident.locationId;
        this.startDate = locationResident.startDate;
        this.endDate = locationResident.endDate;
    }
}
