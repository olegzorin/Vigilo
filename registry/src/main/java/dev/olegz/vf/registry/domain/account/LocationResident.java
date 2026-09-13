package dev.olegz.vf.registry.domain.account;

import dev.olegz.vf.common.Datetime;

public class LocationResident {

    public int locationId;
    public int residentId;
    public Datetime startDate;
    public Datetime endDate;

    @Override
    public String toString() {
        return "{residentId=" + residentId + ", locationId=" + locationId +
            ", startDate=" + startDate +
            (endDate != null ? ", endDate=" + endDate : "") +
            '}';
    }

}
