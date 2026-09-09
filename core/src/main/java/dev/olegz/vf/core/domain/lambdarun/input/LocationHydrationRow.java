package dev.olegz.vf.core.domain.lambdarun.input;

import java.util.Map;

public class LocationHydrationRow {
    public byte rowType;
    public String locationCurrentState;
    public String deviceUuid;
    public Map<String, Object> deviceCurrentState;
    public Integer userId;
    public Byte locationAccess;
}
