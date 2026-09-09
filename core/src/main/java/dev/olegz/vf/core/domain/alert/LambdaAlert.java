package dev.olegz.vf.core.domain.alert;

import java.sql.Timestamp;

public class LambdaAlert {
    public String alertId;
    public String idempotencyKey;
    public String payloadHash;
    public int organizationId;
    public int locationId;
    public String deviceUuid;
    public String alertType;
    public LambdaAlertSeverity severity;
    public String status;
    public String ruleId;
    public Timestamp occurredAt;
    public Timestamp createdAt;
    public int lambdaAssignmentId;
    public int lambdaId;
    public int lambdaVersionId;
    public long runId;
    public String eventKey;
    public String changesJson;
}
