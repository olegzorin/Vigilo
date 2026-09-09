package dev.olegz.vf.core.domain.lambdarun;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LambdaRunContext {
    public int lambdaAssignmentId;
    @JsonProperty("flow")
    public InvocationLane lane = InvocationLane.DEFAULT;
    public long requestId;
    public int lambdaVersionId;
    public int lambdaId;

    @Override
    public String toString() {
        return "requestId=" + requestId + ", lambdaAssignmentId=" + lambdaAssignmentId +
            ", lane=" + lane + ", lambdaVersionId=" + lambdaVersionId + ", lambdaId=" + lambdaId;
    }
}
