package dev.olegz.vf.core.domain.lambdarun.input;

import java.util.Map;

public class LocationDeviceSnapshot {
    public String deviceUuid;
    public int deviceTypeId;
    public String deviceName;
    public int locationId;
    public long startDate;
    public Map<String, Object> currentState;

}
