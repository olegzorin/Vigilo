package dev.olegz.vf.core.domain.lambdarun;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.olegz.vf.common.util.DateFormatUtils;

public class LambdaError {
    public int lambdaAssignmentId;
    @JsonProperty("flow")
    public InvocationLane lane = InvocationLane.DEFAULT;
    public long time;
    public String message;
    public String logs;
    public Integer signature;

    public LambdaError() {}

    public LambdaError(int lambdaAssignmentId, InvocationLane lane, long time, String message) {
        this(lambdaAssignmentId, lane, time, message, null);
    }

    public LambdaError(int lambdaAssignmentId, InvocationLane lane, long time, String message, String logs) {
        this.lambdaAssignmentId = lambdaAssignmentId;
        this.lane = lane;
        this.time = time;
        this.message = message;
        this.logs = logs;
    }

    @Override
    public String toString() {
        return "{lambdaAssignmentId=" + lambdaAssignmentId + ", lane=" + lane + ", time=" + DateFormatUtils.logTimestamp(time) + '}';
    }
}
