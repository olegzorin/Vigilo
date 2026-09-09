package dev.olegz.vf.worker.domain;

import java.sql.Timestamp;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.olegz.vf.core.domain.lambdarun.LambdaRun;
import dev.olegz.vf.core.domain.lambdarun.LambdaRunContext;
import dev.olegz.vf.core.domain.lambdarun.LambdaRunInfo;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;

public class LambdaInvokeRequest {
    // JSON name of the invocationToken field. Must be in sync with LambdaInput.
    private static final Pattern INVOCATION_TOKEN_FIELD_PATTERN = Pattern.compile(
        "(\"invocationToken\":)(\\d{%d})".formatted(LambdaRun.INVOCATION_TOKEN_LENGTH));

    public long requestId;
    public volatile int invocationGen;
    public volatile int retryAttempted;
    public long eventTime;
    public int locationId;
    public int lambdaAssignmentId;
    public int lambdaVersionId;
    public int lambdaId;
    public boolean publicVersion;
    public int memory;
    public String lambdaFunction;
    public int triggers;
    public int eventsCount;
    public String server;
    @JsonProperty("flow")
    public InvocationLane lane = InvocationLane.DEFAULT;
    public long timeout; // in milliseconds
    public String awsRequestId;
    public boolean logging;

    public volatile byte resultCode;
    public volatile String resultMessage;
    public volatile long executionTime;
    public volatile String appInput;
    public volatile boolean shouldRetry;


    public LambdaInvokeRequest() {
    }

    public LambdaInvokeRequest(LambdaRun exec) {
        this.requestId = exec.runId;
        this.invocationGen = exec.invocationGen;
        this.locationId = exec.locationId;
        this.triggers = exec.trigger;
        this.eventsCount = exec.inputs.length;
        this.eventTime = exec.inputs[0].time;
        this.timeout = exec.timeout();
        this.server = ""; // TODO node index;
        this.lane = exec.invocationLane;
        this.lambdaAssignmentId = exec.lambdaAssignmentId;
        this.lambdaVersionId = exec.lambdaVersionId;
        this.lambdaId = exec.lambdaId;
        this.publicVersion = exec.published;
        this.memory = exec.memory;
        this.lambdaFunction = exec.functionName;
        this.logging = (exec.awsLogEndDate != null) && (exec.awsLogEndDate.getTime() > System.currentTimeMillis());
    }

    public boolean canRetry() {
        return retryAttempted < LambdaRun.lambdaMaxRetries();
    }

    public void setResult(byte resultCode, String errorMessage) {
        this.resultCode = resultCode;
        this.resultMessage = errorMessage;
    }

    public void setResult(LambdaOutput lambdaOutput) {
        this.resultCode = lambdaOutput.resultCode();
        this.resultMessage = lambdaOutput.errorMessage;
    }

    public void setResult(byte resultCode, Throwable error) {
        StringBuilder message = new StringBuilder().append(error.getMessage());
        Throwable cause = error.getCause();
        while (cause != null) {
            message.append("; ").append(cause);
            cause = cause.getCause();
        }
        this.resultCode = resultCode;
        this.resultMessage = message.toString();
    }

    @Override
    public String toString() {
        return "{triggers=" + triggers +
            ", requestId=" + requestId +
            ", invocationGen=" + invocationGen +
            ", eventTime=" + eventTime +
            ", locationId=" + locationId +
            (lane == InvocationLane.DEFAULT ? "" : ", lane=" + lane) +
            ", lambdaAssignmentId=" + lambdaAssignmentId +
            ", lambdaVersionId=" + lambdaVersionId +
            ", lambdaId=" + lambdaId +
            (publicVersion ? ", public" : "") +
            (logging ? ", logging" : "") +
            ", memory=" + memory +
            ", function=" + lambdaFunction +
            ", eventsCount=" + eventsCount +
            ", server=" + server +
            ", timeout=" + timeout +
            "\ninput=" + appInput +
            '}';
    }

    public String toLogString() {
        return "{requestId=" + requestId + ", lambdaAssignmentId=" + lambdaAssignmentId + ", lambdaId=" + lambdaId +
            (invocationGen > 0 ? ", invocationGen=" + invocationGen : "") +
            (resultCode != 0 ? ", resultCode=" + resultCode : "") +
            (logging ? ", logging" : "") +
            ", locationId=" + locationId +
            (lane == InvocationLane.DEFAULT ? "" : ", lane=" + lane) +
            ", function=" + lambdaFunction +
            '}';
    }

    /**
     * Returns a detached copy of the stable attempt input without its mutable result fields.
     */
    public LambdaInvokeRequest copyForOutcome() {
        return copyAttemptData();
    }

    /**
     * Returns the next retry as a separate request so a late completion cannot overwrite it.
     */
    public LambdaInvokeRequest createRetryAttempt() {
        LambdaInvokeRequest retry = copyAttemptData();
        retry.retryAttempted++;
        retry.invocationGen++;
        retry.appInput = INVOCATION_TOKEN_FIELD_PATTERN.matcher(retry.appInput)
            .replaceFirst("$1" + LambdaRun.makeInvocationToken(retry.requestId, retry.invocationGen));
        return retry;
    }

    private LambdaInvokeRequest copyAttemptData() {
        LambdaInvokeRequest copy = new LambdaInvokeRequest();
        copy.requestId = requestId;
        copy.invocationGen = invocationGen;
        copy.retryAttempted = retryAttempted;
        copy.eventTime = eventTime;
        copy.locationId = locationId;
        copy.lambdaAssignmentId = lambdaAssignmentId;
        copy.lambdaVersionId = lambdaVersionId;
        copy.lambdaId = lambdaId;
        copy.publicVersion = publicVersion;
        copy.memory = memory;
        copy.lambdaFunction = lambdaFunction;
        copy.triggers = triggers;
        copy.eventsCount = eventsCount;
        copy.server = server;
        copy.lane = lane;
        copy.timeout = timeout;
        copy.awsRequestId = awsRequestId;
        copy.logging = logging;
        copy.appInput = appInput;
        return copy;
    }

    public LambdaRunInfo toLambdaRunInfo() {
        LambdaRunInfo info = new LambdaRunInfo(lambdaId, lambdaAssignmentId, lane, memory);
        info.lambdaVersionId = lambdaVersionId;
        info.requestDate = new Timestamp(requestId);
        info.triggers = triggers;
        info.eventsCount = eventsCount;
        info.eventDate = new Timestamp(eventTime);
        info.server = server;
        info.executionTime = executionTime;
        info.processingTime = System.currentTimeMillis() - info.requestDate.getTime();
        info.retries = retryAttempted;
        info.resultCode = resultCode;
        info.resultMessage = resultMessage;
        info.functionName = lambdaFunction;
        return info;
    }

    public LambdaRunContext runContext() {
        LambdaRunContext context = new LambdaRunContext();
        context.lambdaAssignmentId = this.lambdaAssignmentId;
        context.lane = this.lane;
        context.requestId = this.requestId;
        context.lambdaVersionId = this.lambdaVersionId;
        context.lambdaId = this.lambdaId;
        return context;
    }

}
