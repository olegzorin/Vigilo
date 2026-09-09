package dev.olegz.vf.registry.domain.device;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.registry.domain.account.Location;

public class LocationDevice {
    public String deviceUuid;
    public int locationId;
    public Datetime startDate;
    public Datetime endDate;

    public DeviceType deviceType;
    public Device device;
    public DeviceCurrentState deviceCurrentState;
    public Location location;

    @Override
    public String toString() {
        return "{deviceUuid=" + deviceUuid +
            ", locationId=" + locationId +
            ", startDate=" + startDate +
            ", endDate=" + endDate +
            '}';
    }
}
