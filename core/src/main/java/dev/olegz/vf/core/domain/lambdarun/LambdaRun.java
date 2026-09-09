package dev.olegz.vf.core.domain.lambdarun;

import java.sql.Timestamp;
import java.time.Duration;

import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.props.DurationProp;
import dev.olegz.vf.common.props.IntProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.domain.lambdabuild.InvocationLaneSettings;
import dev.olegz.vf.core.domain.lambdarun.input.LambdaInput;
import dev.olegz.vf.core.domain.lambdarun.input.TriggerEventData;
import dev.olegz.vf.core.event.ResetEvent;
import dev.olegz.vf.core.event.ScheduledEvent;
import dev.olegz.vf.core.event.TriggerEvent;

public class LambdaRun {
    private static final int INVOCATION_GEN_SHIFT = 5;
    private static final int INVOCATION_GEN_MASK = (1 << INVOCATION_GEN_SHIFT) - 1;

    public static final int MAX_INVOCATION_GEN = INVOCATION_GEN_MASK;
    public static final int INVOCATION_TOKEN_LENGTH = Long.toString(makeInvocationToken(System.currentTimeMillis(), 0)).length();

    public enum Status {
        NEW,
        NEXT,
        RETRY,
        OTHER,
        COMPLETED,
        TOO_MANY
    }

    public static int lambdaMaxRetries() {
        return Math.min(MAX_INVOCATION_GEN, PropertyStore.getInt(IntProp.LAMBDA_RETRIES_MAX));
    }

    public static long makeInvocationToken(long runId, int invocationGen) {
        return (runId << INVOCATION_GEN_SHIFT) + (invocationGen & INVOCATION_GEN_MASK);
    }

    public static long getRunIdFromInvocationToken(long invocationToken) {
        return invocationToken >>> INVOCATION_GEN_SHIFT;
    }

    public static int getInvocationGenFromToken(long invocationToken) {
        return (int)(invocationToken & INVOCATION_GEN_MASK);
    }

    public static Duration lambdaRetryDelay() {
        return PropertyStore.getDuration(DurationProp.LAMBDA_RETRIES_DELAY);
    }

    // Retry is not allowed if the number of pending runs exceeds this limit
    public static int lambdaMaxPendingRunsAllowRetry() {
        return PropertyStore.getInt(IntProp.LAMBDA_RETRIES_MAX_PENDING_EXECUTIONS);
    }

    public static Duration lambdaStartTimeout() {
        return PropertyStore.getDuration(DurationProp.LAMBDA_START_TIMEOUT);
    }

    public static Duration executionExpiryGracePeriod() {
        return PropertyStore.getDuration(DurationProp.LAMBDA_EXECUTION_EXPIRY_GRACE_PERIOD);
    }

    public static int getMaxTimeoutsPerHour() {
        return PropertyStore.getInt(IntProp.LAMBDA_MAX_TIMEOUTS_PER_HOUR);
    }

    // database fields
    public int lambdaAssignmentId;
    public InvocationLane invocationLane;
    public int lambdaVersionId;
    public String functionName;
    public int triggerCount;
    public int pendingCount;
    public Long nextInputNumber;
    public long runId;
    public Long prevRunId;
    public int invocationGen;
    public boolean started;
    public String awsRequestId;
    public String awsLogStream;
    public Timestamp triggeredAt;
    public Timestamp expiryDate;
    public Datetime awsLogEndDate;

    // in-memory fields
    public int lambdaId;
    public boolean published;
    public int locationId;
    public Timestamp endDate;
    public Status status;
    public int trigger;
    public int memory;
    public int appTimeout; // in seconds
    public TriggerEventData[] inputs;
    public LambdaRun prevRun;

    public LambdaRun() {
    }

    // Placeholders for a new record in the database
    public LambdaRun(int lambdaAssignmentId, InvocationLane lane) {
        this.lambdaAssignmentId = lambdaAssignmentId;
        this.invocationLane = lane;
        this.lambdaVersionId = Integer.MAX_VALUE;
        this.functionName = "";
        this.runId = 0L;
        this.triggeredAt = new Timestamp(System.currentTimeMillis());
        this.expiryDate = this.triggeredAt;
    }

    private LambdaRun(LambdaRuntimeAssignment lambda, InvocationLane lane) {
        this.runId = System.currentTimeMillis();
        this.lambdaId = lambda.lambdaId;
        this.published = lambda.version.published;
        this.lambdaAssignmentId = lambda.lambdaAssignmentId;
        this.locationId = lambda.locationId;
        this.invocationLane = lane;
        this.lambdaVersionId = lambda.version.lambdaVersionId;
        this.memory = lambda.version.memory;
        this.appTimeout = lambda.version.timeout;
        this.triggeredAt = new Timestamp(System.currentTimeMillis());
        this.expiryDate = new Timestamp(System.currentTimeMillis() + getMaxExecutionTime());
        this.status = Status.NEW;
        this.triggerCount = 1;
        if (lane == InvocationLane.ASYNC && lambda.version.getFunctionName(lane) == null) {
            throw new ApplicationFailureException("Async function name is missing, lambdaVersionId=" + lambda.version.lambdaVersionId);
        }
        this.functionName = lambda.version.getFunctionName(lane);
    }

    public LambdaRun(LambdaRuntimeAssignment lambda, TriggerEvent event, TriggerEventData input) {
        this(lambda, InvocationLane.DEFAULT);
        this.trigger = event.trigger;
        this.inputs = new TriggerEventData[]{input};
    }

    public LambdaRun(LambdaRuntimeAssignment lambda, ResetEvent event, TriggerEventData input) {
        this(lambda, InvocationLane.DEFAULT);
        this.trigger = ResetEvent.TRIGGER;
        this.inputs = new TriggerEventData[]{input};
    }

    public LambdaRun(LambdaRuntimeAssignment lambda, ScheduledEvent event, TriggerEventData input) {
        this(lambda, InvocationLane.ASYNC);
        this.trigger = TriggerEvent.TRIGGER_SCHEDULE;
        this.inputs = new TriggerEventData[]{input};
    }

    public LambdaRun(LambdaRuntimeAssignment lambda, InvocationLane lane, LambdaRun prevRun) {
        this(lambda, lane);
        this.prevRunId = prevRun.runId;
        this.prevRun = prevRun;
        this.nextInputNumber = prevRun.nextInputNumber;
        this.pendingCount = prevRun.pendingCount;
        this.triggerCount = prevRun.triggerCount + 1;
        this.status = Status.NEXT;
        this.awsLogEndDate = prevRun.awsLogEndDate;
    }

    public void requireInput() {
        if ((inputs == null) || (inputs.length == 0)) {
            throw new ApplicationFailureException("No inputs for trigger=" + trigger + ", status=" + status +
                ", invocationLane=" + invocationLane + ", lambdaAssignmentId" + lambdaAssignmentId);
        }
        for (TriggerEventData input : inputs) {
            if (input == null) {
                throw new ApplicationFailureException("Input contains null for trigger=" + trigger + ", status=" + status +
                    ", invocationLane=" + invocationLane + ", lambdaAssignmentId" + lambdaAssignmentId);
            }
        }
    }

    private long getMaxExecutionTime() {
        Duration timeout = invocationLane == InvocationLane.ASYNC
            ? Duration.ofSeconds(InvocationLaneSettings.forLane(invocationLane).timeout())
                .plus(PropertyStore.getDuration(DurationProp.LAMBDA_ASYNC_START_TIMEOUT))
            : lambdaStartTimeout().plusSeconds(appTimeout);
        return timeout.toMillis();
    }

    public LambdaInput toLambdaInput() {
        LambdaInput input = new LambdaInput();
        input.id = lambdaAssignmentId;
        input.lane = invocationLane;
        input.lambdaId = lambdaId;
        input.lambdaVersionId = lambdaVersionId;
        input.runId = runId;
        input.count = triggerCount;
        input.inputs = inputs;
        if ((prevRun != null) && (prevRun.triggeredAt != null)) {
            input.prevTriggerTime = prevRun.triggeredAt.getTime();
        }
        input.invocationToken = makeInvocationToken(runId, invocationGen);
        if ((awsLogEndDate != null) && (awsLogEndDate.getTime() > System.currentTimeMillis())) {
            input.logEvents = true;
        }
        return input;
    }

    public LambdaRunContext toRunContext() {
        LambdaRunContext context = new LambdaRunContext();
        context.lambdaAssignmentId = this.lambdaAssignmentId;
        context.lane = this.invocationLane;
        context.requestId = this.runId;
        context.lambdaVersionId = this.lambdaVersionId;
        context.lambdaId = this.lambdaId;
        return context;
    }

    @Override
    public String toString() {
        return "{lambdaAssignmentId=" + lambdaAssignmentId +
            (invocationLane != InvocationLane.DEFAULT ? ", invocationLane=" + invocationLane : "") +
            ", runId=" + runId +
            (prevRunId != null ? ", prevRunId=" + prevRunId : "") +
            ", expiryDate=" + expiryDate +
            (invocationGen != 0 ? ", invocationGen=" + invocationGen : "") +
            (started ? ", started" : "") +
            (triggerCount > 1 ? ", triggerCount=" + triggerCount : "") +
            (pendingCount != 0 ? ", pendingCount=" + pendingCount : "") +
            (nextInputNumber != null ? ", nextInputNumber=" + nextInputNumber : "") +
            ", lambdaVersionId=" + lambdaVersionId +
            ", trigger=" + trigger +
            ", locationId=" + locationId +
            (inputs != null ? ", inputsLen=" + inputs.length : "") +
            (status != null ? ", status=" + status : "") +
            '}';
    }

    public int maxInputsPerRun() {
        int batchSize = invocationLane == InvocationLane.ASYNC ? 1 :
            PropertyStore.getInt(IntProp.LOC_LAMBDA_MAX_INPUTS_PER_REQUEST);

        return Math.max(batchSize, 1);
    }

    public static final int MAX_PENDING_INPUTS = 0x10000;

    public int maxPendingInputs() {
        return Math.min(MAX_PENDING_INPUTS, PropertyStore.getInt(IntProp.LAMBDA_MAX_PENDING_EXECUTIONS) * maxInputsPerRun());
    }

    public boolean running() {
        return (runId != 0) && (expiryDate.getTime() > System.currentTimeMillis());
    }

    public long timeout() {
        return expiryDate.getTime() - triggeredAt.getTime();
    }

    public static String cloudWatchLogGroup(int lambdaId) {
        return "/Lambdas/botlab/" + lambdaId + '/';
    }

    public static String cloudWatchLogStream(int lambdaAssignmentId, InvocationLane lane) {
        return "Id=" + lambdaAssignmentId + ";flow=" + lane.code();
    }
}
