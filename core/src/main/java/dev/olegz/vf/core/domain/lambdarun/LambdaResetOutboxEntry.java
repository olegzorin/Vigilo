package dev.olegz.vf.core.domain.lambdarun;

import java.sql.Timestamp;

/** Assignment reset waiting for acknowledged Kafka publication. */
public class LambdaResetOutboxEntry {
    public long id;
    public int locationId;
    public String eventId;
    public long eventTime;
    public int lambdaAssignmentId;
    public long variableGeneration;
    public Timestamp createdAt;
    public String claimId;
    public Timestamp claimUntil;

    public LambdaResetOutboxEntry() {
    }

    public LambdaResetOutboxEntry(
        int locationId,
        String eventId,
        long eventTime,
        int lambdaAssignmentId,
        long variableGeneration,
        Timestamp createdAt)
    {
        this.locationId = locationId;
        this.eventId = eventId;
        this.eventTime = eventTime;
        this.lambdaAssignmentId = lambdaAssignmentId;
        this.variableGeneration = variableGeneration;
        this.createdAt = createdAt;
    }
}
