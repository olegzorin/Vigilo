package dev.olegz.vf.core.event;

public class ResetEvent {
    public static final int TRIGGER = 0;

    public String eventId;
    public long time;
    public int locationId;
    public int lambdaAssignmentId;
    public long variableGeneration;

    public ResetEvent() {
    }

    public ResetEvent(int locationId, int lambdaAssignmentId) {
        this.locationId = locationId;
        this.lambdaAssignmentId = lambdaAssignmentId;
    }

    public long variableGeneration() {
        return variableGeneration != 0 ? variableGeneration : time;
    }

    @Override
    public String toString() {
        return "{eventId=" + eventId +
            ", time=" + time +
            ", locationId=" + locationId +
            ", lambdaAssignmentId=" + lambdaAssignmentId +
            ", variableGeneration=" + variableGeneration() +
            '}';
    }
}
