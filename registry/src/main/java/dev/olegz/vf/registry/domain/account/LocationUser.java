package dev.olegz.vf.registry.domain.account;

import dev.olegz.vf.common.Datetime;

public class LocationUser {

    public int locationId;
    public int userId;
    public Datetime startDate;
    public Datetime endDate;

    @Override
    public String toString() {
        return "{userId=" + userId + ", locationId=" + locationId +
            ", startDate=" + startDate +
            (endDate != null ? ", endDate=" + endDate : "") +
            '}';
    }

}
