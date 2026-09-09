package dev.olegz.vf.core.service.lambda;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.props.IntProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.dao.LambdaInvokeRetryOutboxDao;
import dev.olegz.vf.core.dao.LambdaRunStateDao;
import dev.olegz.vf.core.dao.metric.LambdaWorkloadMetric;
import dev.olegz.vf.registry.dao.retry.RetryOnConcurrencyFailure;
import dev.olegz.vf.core.domain.lambdarun.*;
import dev.olegz.vf.core.domain.lambdarun.input.TriggerEventData;
import dev.olegz.vf.core.event.ResetEvent;
import dev.olegz.vf.core.messaging.MessageDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static dev.olegz.vf.core.dao.metric.LambdaWorkloadMetric.Operation.*;

@Service
public class LambdaRunStateServiceImpl implements LambdaRunStateService {
    private static final Logger logger = LoggerFactory.getLogger(LambdaRunStateServiceImpl.class);

    private final LambdaRunStateDao stateDao;
    private final LambdaInvokeRetryOutboxDao retryOutboxDao;
    private final Consumer<LambdaError> lambdaErrorRegistrar;

    @Autowired
    public LambdaRunStateServiceImpl(LambdaRunStateDao stateDao, LambdaInvokeRetryOutboxDao retryOutboxDao) {
        this(stateDao, retryOutboxDao, MessageDispatcher::sendLambdaError);
    }

    // For unit tests
    LambdaRunStateServiceImpl(
        LambdaRunStateDao stateDao,
        LambdaInvokeRetryOutboxDao retryOutboxDao,
        Consumer<LambdaError> lambdaErrorRegistrar)
    {
        this.stateDao = stateDao;
        this.retryOutboxDao = retryOutboxDao;
        this.lambdaErrorRegistrar = lambdaErrorRegistrar;
    }

    private LambdaRun getLambdaRunForUpdate(int lambdaAssignmentId, InvocationLane lane, boolean autoCreate) {
        LambdaRun run = stateDao.getLambdaRunForUpdate(lambdaAssignmentId, lane);
        if (run != null) return run;

        if (!autoCreate) {
            throw new ApplicationFailureException("Record not found for lambda run for lambdaAssignmentId=" + lambdaAssignmentId + ", lane=" + lane);
        }

        try {
            stateDao.insertLambdaRun(new LambdaRun(lambdaAssignmentId, lane));
        } catch (DuplicateKeyException ignore) {
        }

        return stateDao.getLambdaRunForUpdate(lambdaAssignmentId, lane);
    }

    @Override
    @RetryOnConcurrencyFailure("")
    @LambdaWorkloadMetric(operation = workflowPutNewRun)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean putNewRun(LambdaRun newRun) {
        return putNewRunInternal(newRun);
    }

    @Override
    @LambdaWorkloadMetric(operation = workflowPutNewRun)
    @Transactional(propagation = Propagation.REQUIRED)
    public boolean putNewRunTransactional(LambdaRun newRun) {
        return putNewRunInternal(newRun);
    }

    private boolean putNewRunInternal(LambdaRun newRun) {
        // Note! We never call readPendingInputs in this method.
        // This means that pending inputs that may have existed will be discarded if
        // a reset trigger is received. Expired runs remain authoritative until durable
        // completion recovery advances them, so a new input must be queued behind them.

        final InvocationLane lane = newRun.invocationLane;
        final int lambdaAssignmentId = newRun.lambdaAssignmentId;

        LambdaRun run = getLambdaRunForUpdate(lambdaAssignmentId, lane, true);

        if (((run.runId == 0L) || (ResetEvent.TRIGGER == newRun.trigger)) && stateDao.updateLambdaRun(newRun)) {
            newRun.awsLogEndDate = run.awsLogEndDate;
            newRun.prevRun = run;
            return true;
        }

        // Enqueue the input and return false

        if (run.pendingCount < run.maxPendingInputs()) {
            LambdaPendingInput input = new LambdaPendingInput(newRun);
            long inputNumber = run.runId * LambdaRun.MAX_PENDING_INPUTS + run.pendingCount;
            LambdaPendingInputRecord inputRecord = LambdaPendingInputRecord.fromInput(inputNumber, input);
            boolean large = LambdaPendingInputRecord.isLarge(inputRecord.inputSize);
            int paddedSize = LambdaPendingInputRecord.paddedSizeOf(inputRecord.inputSize);
            long nextInputNumber = run.nextInputNumber == null ? inputNumber : run.nextInputNumber;

            stateDao.enqueuePendingInput(lambdaAssignmentId, lane, inputRecord, large, paddedSize,
                nextInputNumber, LambdaPendingInputRecord.getInsertPart());

            stateDao.updateLambdaRunPendingCount(lambdaAssignmentId, lane, run.pendingCount + 1, nextInputNumber);

            if (logger.isDebugEnabled()) {
                logger.debug("putNewRun() saved pending input for lambdaAssignmentId=" + lambdaAssignmentId + ", lane=" + lane);
            }
        } else {
            String message = "Too many pending inputs; the lambda input was discarded because the pending execution limit was reached";
            logger.error(message + ": lambdaId=" + newRun.lambdaId + ", lambdaVersionId=" + newRun.lambdaVersionId +
                ", lambdaAssignmentId=" + newRun.lambdaAssignmentId + ", lane=" + lane +
                ", locationId=" + newRun.locationId);
            long errorTime = newRun.triggeredAt != null ? newRun.triggeredAt.getTime() : System.currentTimeMillis();
            lambdaErrorRegistrar.accept(new LambdaError(lambdaAssignmentId, lane, errorTime, message));
        }

        return false;
    }

    @Override
    public boolean checkRunFinal(int lambdaAssignmentId, InvocationLane lane, long prevRunId) {
        return stateDao.markLambdaRunComplete(lambdaAssignmentId, lane, prevRunId);
    }

    @Override
    @LambdaWorkloadMetric(operation = workflowPutNextRun)
    @Transactional(propagation = Propagation.REQUIRED)
    public LambdaRun putNextRunTransactional(
        int lambdaAssignmentId,
        InvocationLane lane,
        LambdaRuntimeAssignment lambda,
        long prevRunId)
    {
        return putNextRunInternal(lambdaAssignmentId, lane, lambda, prevRunId);
    }

    private LambdaRun putNextRunInternal(
        int lambdaAssignmentId,
        InvocationLane lane,
        LambdaRuntimeAssignment lambda,
        long prevRunId)
    {
        LambdaRun run = getLambdaRunForUpdate(lambdaAssignmentId, lane, false);

        if (run.runId != prevRunId) {
            if ((run.prevRunId != null) && (run.prevRunId == prevRunId)) {
                logger.warn("putNextRun() duplicate call {}", run);
            } else {
                logger.warn("putNextRun() prevRunId=" + prevRunId + " but actual: " + run);
            }
            return null;
        }

        if ((run.pendingCount == 0) || (run.nextInputNumber == null)) {
            stateDao.markLambdaRunComplete(run.lambdaAssignmentId, run.invocationLane, null);
            return null;
        }

        if (lambda == null) {
            stateDao.markLambdaRunComplete(run.lambdaAssignmentId, run.invocationLane, null);
            if (logger.isDebugEnabled()) logger.warn("putNextRun() inactive, lambdaAssignmentId=" + lambdaAssignmentId + ", lane=" + lane);
            return null;
        }

        LambdaRun newRun = new LambdaRun(lambda, lane, run);
        readPendingInputs(newRun);
        if (newRun.inputs == null) {
            logger.warn("putNextRun() no readable messages, lambdaAssignmentId=" + lambdaAssignmentId + ", lane=" + lane);
            stateDao.markLambdaRunComplete(run.lambdaAssignmentId, run.invocationLane, null);
            return null;
        }

        // A completion redelivery must recreate the same request identity if Kafka accepted the
        // continuation but this database transaction did not commit.
        newRun.runId = continuationRunId(run.runId, newRun.inputs[0]);

        stateDao.updateLambdaRun(newRun);
        return newRun;
    }

    static long continuationRunId(long previousRunId, TriggerEventData firstInput) {
        return Math.max(previousRunId + 1L, firstInput.time);
    }

    private void readPendingInputs(LambdaRun run) {
        int maxCount = Math.min(run.pendingCount, run.maxInputsPerRun());
        boolean sorted = run.pendingCount > maxCount;
        List<LambdaPendingInput> inputs = readPendingInputs(
            run.lambdaAssignmentId, run.invocationLane, run.nextInputNumber, maxCount, sorted);
        if (inputs == null) {
            logger.warn("Pending inputs not found for lambdaAssignmentId=" + run.lambdaAssignmentId +
                ", invocationLane=" + run.invocationLane + ". expected " + run.pendingCount);
            return;
        }

        int drainedCount = inputs.size();
        long lastInputNumber = inputs.get(drainedCount - 1).inputNumber;
        run.nextInputNumber = lastInputNumber + 1L;
        run.pendingCount -= drainedCount;

        int write = 0;
        for (LambdaPendingInput input : inputs) {
            if ((input != null) && (input.eventData != null)) {
                inputs.set(write++, input);
            }
        }
        if (write < inputs.size()) inputs.subList(write, inputs.size()).clear();

        if (inputs.isEmpty()) {
            logger.error("All drained pending inputs discarded (oversized or unparseable) for lambdaAssignmentId=" +
                run.lambdaAssignmentId + ", invocationLane=" + run.invocationLane);
            return;
        }

        run.inputs = new TriggerEventData[inputs.size()];
        int ind = 0;
        for (LambdaPendingInput input : inputs) {
            run.inputs[ind++] = input.eventData;
            run.trigger |= input.eventData.trigger;
        }
    }

    private List<LambdaPendingInput> readPendingInputs(int lambdaAssignmentId, InvocationLane lane, long nextInputNumber, int maxCount, boolean sorted) {
        List<LambdaPendingInputRecord> records = stateDao.getPendingInputs(lambdaAssignmentId, lane, nextInputNumber,
            maxCount, sorted, LambdaPendingInputRecord.getReadPartitions());

        if ((records == null) || records.isEmpty()) return null;

        if ((records.size() > 1) && !sorted) {
            records.sort(Comparator.comparingLong(m -> m.inputNumber));
        }

        int maxDataSize = PropertyStore.getInt(IntProp.LAMBDA_MAX_INPUT_DATA_SIZE_BYTES);

        ArrayList<LambdaPendingInput> inputs = new ArrayList<>(records.size());
        for (var record : records) {
            LambdaPendingInput input;
            if (record.inputSize > maxDataSize) {
                logger.warn("Pending input is too large (" + record.inputSize + ") for lambdaAssignmentId=" + lambdaAssignmentId + ", lane=" + lane);
                input = record.placeholderInput();
            } else {
                try {
                    input = record.toInput();
                } catch (Exception e) {
                    logger.error("Exception parsing pending input for lambdaAssignmentId=" + lambdaAssignmentId + ", lane=" + lane + '\n' + e);
                    input = record.placeholderInput();
                }
            }
            inputs.add(input);
        }

        return inputs;
    }

    @Override
    @RetryOnConcurrencyFailure("")
    @LambdaWorkloadMetric(operation = workflowUpdateLambdaRunRetry)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LambdaRun.Status updateLambdaRunRetry(
        int lambdaAssignmentId,
        InvocationLane lane,
        long runId,
        int invocationGen,
        long timeout,
        byte[] retryPayload)
    {
        LambdaRun run = stateDao.getLambdaRunForUpdate(lambdaAssignmentId, lane);

        if (run.runId != runId) {
            if ((run.prevRunId != null) && (run.prevRunId == runId)) {
                logger.warn("updateLambdaRunRetry() run already completed {}", run);
                return LambdaRun.Status.COMPLETED;
            } else {
                logger.warn("updateLambdaRunRetry() other run already started " + run);
                return LambdaRun.Status.OTHER;
            }
        }

        if (run.pendingCount > LambdaRun.lambdaMaxPendingRunsAllowRetry() * run.maxInputsPerRun()) {
            logger.warn("updateLambdaRunRetry() too many pending inputs " + run);
            return LambdaRun.Status.TOO_MANY;
        }

        Timestamp retryAt = new Timestamp(System.currentTimeMillis() + LambdaRun.lambdaRetryDelay().toMillis());
        Timestamp expiryDate = new Timestamp(retryAt.getTime() + timeout);
        stateDao.updateLambdaRunForRetry(lambdaAssignmentId, lane, invocationGen, expiryDate);

        LambdaInvokeRetryOutboxEntry entry =
            new LambdaInvokeRetryOutboxEntry(lambdaAssignmentId, lane, runId, invocationGen, retryPayload, retryAt);
        if (!retryOutboxDao.exists(entry)) {
            retryOutboxDao.insert(entry);
        }

        return LambdaRun.Status.RETRY;
    }

    @Override
    @LambdaWorkloadMetric(operation = workflowMarkLambdaRunNotStartedIfUnchanged)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LambdaRun markLambdaRunNotStartedIfUnchanged(int lambdaAssignmentId, InvocationLane lane, long runId, int initialInvocationGen) {
        LambdaRun run = stateDao.getLambdaRunForUpdate(lambdaAssignmentId, lane);
        if ((run.runId == runId) && (run.invocationGen == initialInvocationGen)) {
            run.invocationGen++;
            run.started = false;
            stateDao.markLambdaRunNotStarted(lambdaAssignmentId, lane);
        }
        return run;
    }

}
