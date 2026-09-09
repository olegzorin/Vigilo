package dev.olegz.vf.core.event;

import java.util.List;

public class ScheduledEvent {
    public String eventId;
    public long time;
    public int locationId;
    public int lambdaAssignmentId;
    public List<String> scheduleIds;

    public ScheduledEvent() {
    }

    public ScheduledEvent(int locationId, int lambdaAssignmentId, List<String> scheduleIds) {
        this.locationId = locationId;
        this.lambdaAssignmentId = lambdaAssignmentId;
        this.scheduleIds = List.copyOf(scheduleIds);
    }

    @Override
    public String toString() {
        return "{time=" + time +
            ", eventId=" + eventId +
            ", locationId=" + locationId +
            ", lambdaAssignmentId=" + lambdaAssignmentId +
            ", scheduleIds=" + scheduleIds +
            '}';
    }
}
