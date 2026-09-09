package dev.olegz.vf.core.domain.lambdarun.input;

import java.util.List;
import java.util.Map;

public class TriggerEventData {
    // Auto-generated
    public String key;

    // From TriggerEvent
    public long time;
    public int trigger;
    public int locationId;
    public String newLocationState;
    public String deviceUuid;
    public Map<String, Object> newDeviceState;

    // From DB
    public LocationSnapshot location;
    public List<LocationDeviceSnapshot> locationDevices;
    public List<LocationUserSnapshot> locationUsers;
    public List<String> scheduleIds;

}
