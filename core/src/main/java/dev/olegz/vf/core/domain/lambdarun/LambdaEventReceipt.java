package dev.olegz.vf.core.domain.lambdarun;

public class LambdaEventReceipt {
    public static final String OPEN = "OPEN";
    public static final String PENDING = "PENDING";
    public static final String ADMITTED = "ADMITTED";
    public static final String COMPLETED = "COMPLETED";

    public String eventId;
    public int lambdaAssignmentId;
    public String status;
    public Long runId;

    public boolean completed() {
        return COMPLETED.equals(status);
    }
}
