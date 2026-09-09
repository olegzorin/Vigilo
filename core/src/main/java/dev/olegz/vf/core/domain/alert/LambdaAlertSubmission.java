package dev.olegz.vf.core.domain.alert;

import java.util.List;

public class LambdaAlertSubmission {
    public String idempotencyKey;
    public String ruleId;
    public LambdaAlertSeverity severity;
    public int locationId;
    public long occurredAt;
    public long runId;
    public String eventKey;
    public List<LambdaAlertReason> reasons;
}
