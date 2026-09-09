package dev.olegz.vf.registry.domain.device;

import java.util.Map;

import dev.olegz.vf.common.Datetime;

public class DeviceCurrentState {
    public String deviceUuid;
    public Map<String, Object> state;
    public Datetime measuredAt;
    public Datetime receivedAt;
}
