package dev.olegz.vf.core.domain.alert;

public class LambdaAlertReason {
    public LambdaAlertResourceType resourceType;
    public String resourceId;
    public String field;
    public Object previousValue;
    public Object newValue;
}
