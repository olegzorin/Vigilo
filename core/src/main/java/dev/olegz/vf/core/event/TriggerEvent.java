package dev.olegz.vf.core.event;

import java.util.Map;

public class TriggerEvent {
    public static final int TRIGGER_SCHEDULE = 1;
    public static final int TRIGGER_LOCATION_EVENT = 1 << 1;   // 2
    public static final int TRIGGER_DEVICE_EVENT = 1 << 2;   // 4

    public String eventId;
    public long time;
    public int trigger;
    public int locationId;
    public String newLocationState;
    public String deviceUuid;
    public Map<String, Object> newDeviceState;

    public TriggerEvent() {
    }

    public TriggerEvent(int trigger, int locationId) {
        this.trigger = trigger;
        this.locationId = locationId;
    }

    public boolean checkTrigger(int trigger) {
        return (this.trigger & trigger) != 0;
    }

    @Override
    public String toString() {
        return "{eventId=" + eventId +
            ", time=" + time +
            ", trigger=" + trigger +
            ", locationId=" + locationId +
            (newLocationState != null ? ", newLocationState=" + newLocationState : "") +
            (deviceUuid != null ? ", deviceUuid=" + deviceUuid : "") +
            '}';
    }
}
