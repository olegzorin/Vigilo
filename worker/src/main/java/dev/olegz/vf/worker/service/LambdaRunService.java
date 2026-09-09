package dev.olegz.vf.worker.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import dev.olegz.vf.aws.error.AwsExceptions;
import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.monitor.EventRateMonitor;
import dev.olegz.vf.common.monitor.EventRateTrigger;
import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.common.objectmap.StringMapper;
import dev.olegz.vf.common.props.DurationProp;
import dev.olegz.vf.common.props.IntProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.common.util.DateFormatUtils;
import dev.olegz.vf.core.dao.LambdaRunDao;
import dev.olegz.vf.core.domain.lambdabuild.InvocationLaneSettings;
import dev.olegz.vf.core.domain.lambdarun.*;
import dev.olegz.vf.core.domain.lambdarun.input.LambdaInput;
import dev.olegz.vf.core.domain.lambdarun.input.TriggerEventData;
import dev.olegz.vf.core.event.ResetEvent;
import dev.olegz.vf.core.event.ScheduledEvent;
import dev.olegz.vf.core.event.TriggerEvent;
import dev.olegz.vf.core.messaging.MessageDispatcher;
import dev.olegz.vf.core.service.lambda.LambdaClientService;
import dev.olegz.vf.core.service.lambda.LambdaLogService;
import dev.olegz.vf.core.service.lambda.LambdaRunCompletionOutboxService;
import dev.olegz.vf.core.service.lambda.LambdaRunStateService;
import dev.olegz.vf.messaging.ConfirmingMessageProducer;
import dev.olegz.vf.messaging.Messaging;
import dev.olegz.vf.messaging.MessagingProvider;
import dev.olegz.vf.messaging.Topics;
import dev.olegz.vf.worker.domain.LambdaInvocationOutcome;
import dev.olegz.vf.worker.domain.LambdaInvokeRequest;
import dev.olegz.vf.worker.domain.LambdaOutput;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("lambdaRunService")
public class LambdaRunService {
    private static final Logger logger = LoggerFactory.getLogger(LambdaRunService.class);

    private static final long ONE_HOUR_MILLIS = Duration.ofHours(1).toMillis();
    private static final ConcurrentHashMap<String, EventRateMonitor> rateMonitors = new ConcurrentHashMap<>(100);
    private final LambdaClientService lambdaClientService;
    private final LambdaRunDao lambdaRunDao;
    private final LambdaRunStateService lambdaRunStateService;
    private final LambdaFunctionInvoker lambdaFunctionInvoker;
    private final LambdaRunContinuationCoordinator continuationCoordinator;
    private final LambdaRunCompletionOutboxService completionOutboxService;

    private static final int REQUEST_THREADS = PropertyStore.getInt("vf.lambda.invoke.requestThreads", 100);

    private final ScheduledThreadPoolExecutor requestTimeoutScheduler = new ScheduledThreadPoolExecutor(5);
    private final ThreadPoolExecutor requestExecutor =
        new ThreadPoolExecutor(REQUEST_THREADS, REQUEST_THREADS, 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>());
    private final AtomicBoolean shutdownInProgress = new AtomicBoolean();

    @Autowired
    public LambdaRunService(LambdaClientService lambdaClientService, LambdaRunDao lambdaRunDao,
        LambdaRunStateService lambdaRunStateService, LambdaFunctionInvoker lambdaFunctionInvoker,
        LambdaRunContinuationCoordinator continuationCoordinator,
        LambdaRunCompletionOutboxService completionOutboxService)
    {
        this(lambdaClientService, lambdaRunDao, lambdaRunStateService, lambdaFunctionInvoker, continuationCoordinator, completionOutboxService,
            Messaging.producer(MessagingProvider.KAFKA, "lambdaExec", ConfirmingMessageProducer.class));
    }

    public LambdaRunService(LambdaClientService lambdaClientService, LambdaRunDao lambdaRunDao,
        LambdaRunStateService lambdaRunStateService, LambdaFunctionInvoker lambdaFunctionInvoker)
    {
        this(lambdaClientService, lambdaRunDao, lambdaRunStateService, lambdaFunctionInvoker, null, null,
            Messaging.producer(MessagingProvider.KAFKA, "lambdaExec", ConfirmingMessageProducer.class));
    }

    LambdaRunService(LambdaClientService lambdaClientService, LambdaRunDao lambdaRunDao,
        LambdaRunStateService lambdaRunStateService, LambdaFunctionInvoker lambdaFunctionInvoker,
        ConfirmingMessageProducer producer)
    {
        this(lambdaClientService, lambdaRunDao, lambdaRunStateService, lambdaFunctionInvoker, null, null, producer);
    }

    LambdaRunService(LambdaClientService lambdaClientService, LambdaRunDao lambdaRunDao,
        LambdaRunStateService lambdaRunStateService, LambdaFunctionInvoker lambdaFunctionInvoker,
        LambdaRunContinuationCoordinator continuationCoordinator,
        LambdaRunCompletionOutboxService completionOutboxService,
        ConfirmingMessageProducer producer)
    {
        this.lambdaClientService = lambdaClientService;
        this.lambdaRunDao = lambdaRunDao;
        this.lambdaRunStateService = lambdaRunStateService;
        this.lambdaFunctionInvoker = lambdaFunctionInvoker;
        this.continuationCoordinator = continuationCoordinator;
        this.completionOutboxService = completionOutboxService;
        this.producer = producer;
        requestExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        requestTimeoutScheduler.setRemoveOnCancelPolicy(true);
    }

    @PreDestroy
    public void shutdown() {
        if (!shutdownInProgress.compareAndSet(false, true)) return;

        requestExecutor.shutdown();
        requestTimeoutScheduler.shutdownNow();
        try {
            if (!requestExecutor.awaitTermination(15, TimeUnit.SECONDS)) {
                requestExecutor.shutdownNow();
            }
            if (!requestTimeoutScheduler.awaitTermination(15, TimeUnit.SECONDS)) {
                requestTimeoutScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            requestExecutor.shutdownNow();
            requestTimeoutScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        lambdaFunctionInvoker.destroy();
    }

    void submitLambdaInput(
        LambdaRuntimeAssignment lambdaAssignment,
        TriggerEvent event,
        TriggerEventData eventData,
        BiConsumer<LambdaRun, LambdaRuntimeAssignment> execConsumer)
    {
        submitLambdaInput(
            new LambdaRun(lambdaAssignment, event, eventData),
            lambdaAssignment,
            eventData,
            "Cloud",
            execConsumer);
    }

    long admitDurableLambdaInput(
        LambdaRuntimeAssignment lambdaAssignment,
        TriggerEvent event,
        TriggerEventData eventData)
    {
        return admitDurableLambdaInput(new LambdaRun(lambdaAssignment, event, eventData));
    }

    long admitDurableLambdaInput(
        LambdaRuntimeAssignment lambdaAssignment,
        ResetEvent event,
        TriggerEventData eventData)
    {
        return admitDurableLambdaInput(new LambdaRun(lambdaAssignment, event, eventData));
    }

    private long admitDurableLambdaInput(LambdaRun run) {
        return lambdaRunStateService.putNewRunTransactional(run) ? run.runId : 0L;
    }

    void submitLambdaInput(
        LambdaRuntimeAssignment lambdaAssignment,
        ResetEvent event,
        TriggerEventData eventData,
        BiConsumer<LambdaRun, LambdaRuntimeAssignment> execConsumer)
    {
        submitLambdaInput(
            new LambdaRun(lambdaAssignment, event, eventData),
            lambdaAssignment,
            eventData,
            "Reset",
            execConsumer);
    }

    long admitDurableLambdaInput(
        LambdaRuntimeAssignment lambdaAssignment,
        ScheduledEvent event,
        TriggerEventData eventData)
    {
        return admitDurableLambdaInput(new LambdaRun(lambdaAssignment, event, eventData));
    }

    private void submitLambdaInput(
        LambdaRun run,
        LambdaRuntimeAssignment lambdaAssignment,
        TriggerEventData eventData,
        String inputType,
        BiConsumer<LambdaRun, LambdaRuntimeAssignment> execConsumer)
    {
        if (lambdaRunStateService.putNewRun(run)) {
            if (logger.isDebugEnabled()) {
                logger.debug("||| submit{}Input()\nlambda: {}\ninput: {}", inputType, lambdaAssignment, eventData);
            }
            execConsumer.accept(run, lambdaAssignment);
        } else if (logger.isDebugEnabled()) {
            logger.debug(
                "||| submit{}Input() sent to pending queue\nlambda: {}\ninput: {}",
                inputType,
                lambdaAssignment,
                eventData);
        }
    }

    private static final String RS_API_HOST;

    static {
        String host = PropertyStore.getString("vf.host.api", true);
        if (host == null) {
            throw new ApplicationFailureException("API host not set");
        }
        RS_API_HOST = host;
    }

    public void processRunCompletion(LambdaRunContext completed) {
        LambdaRuntimeAssignment lambdaAssignment =
            lambdaRunDao.getActiveLambdaRuntimeAssignment(completed.lambdaAssignmentId);
        continuationCoordinator.completeAndAdvance(
            completed,
            lambdaAssignment,
            run -> producer.sendAndAwait(
                Topics.LAMBDA_INVOKE_REQUEST, createCloudRunRequestPayload(run, lambdaAssignment)));
    }

    private final ConfirmingMessageProducer producer;

    boolean publishAdmittedLambdaInput(
        LambdaRuntimeAssignment lambdaAssignment,
        TriggerEvent event,
        TriggerEventData eventData,
        long runId)
    {
        return publishAdmittedLambdaInput(new LambdaRun(lambdaAssignment, event, eventData), lambdaAssignment, runId);
    }

    boolean publishAdmittedLambdaInput(
        LambdaRuntimeAssignment lambdaAssignment,
        ResetEvent event,
        TriggerEventData eventData,
        long runId)
    {
        return publishAdmittedLambdaInput(new LambdaRun(lambdaAssignment, event, eventData), lambdaAssignment, runId);
    }

    boolean publishAdmittedLambdaInput(
        LambdaRuntimeAssignment lambdaAssignment,
        ScheduledEvent event,
        TriggerEventData eventData,
        long runId)
    {
        return publishAdmittedLambdaInput(new LambdaRun(lambdaAssignment, event, eventData), lambdaAssignment, runId);
    }

    private boolean publishAdmittedLambdaInput(LambdaRun run, LambdaRuntimeAssignment lambdaAssignment, long runId) {
        LambdaRun persisted = lambdaRunDao.getLambdaRun(lambdaAssignment.lambdaAssignmentId, run.invocationLane);
        if ((persisted == null) || (persisted.runId != runId)) return false;

        run.runId = persisted.runId;
        run.invocationGen = persisted.invocationGen;
        run.triggerCount = persisted.triggerCount;
        run.lambdaVersionId = persisted.lambdaVersionId;
        run.functionName = persisted.functionName;
        run.awsLogEndDate = persisted.awsLogEndDate;
        producer.sendAndAwait(Topics.LAMBDA_INVOKE_REQUEST, createCloudRunRequestPayload(run, lambdaAssignment));
        return true;
    }

    private byte[] createCloudRunRequestPayload(LambdaRun run, LambdaRuntimeAssignment lambdaAssignment) {
        // Lambda input must exist
        run.requireInput();
        LambdaInvokeRequest request = new LambdaInvokeRequest(run);
        LambdaInput lambdaInput = run.toLambdaInput();

        // lambda key expiry in seconds
        int keyExpiry = run.invocationLane == InvocationLane.ASYNC
            ? Math.toIntExact(PropertyStore.getDuration(DurationProp.LAMBDA_ASYNC_KEY_EXPIRY).toSeconds())
            : Math.toIntExact((LambdaRun.lambdaStartTimeout().toSeconds() + LambdaRun.lambdaRetryDelay().toSeconds() + 1 +
                lambdaAssignment.version.timeout) * LambdaRun.lambdaMaxRetries());

        LambdaKeyInput lambdaKey = lambdaClientService.createLambdaKey(
            lambdaAssignment, keyExpiry, run.invocationLane, lambdaInput.triggers());
        if ((lambdaKey == null) || (lambdaKey.key == null) || lambdaKey.key.isBlank()) {
            throw new ApplicationFailureException(
                "Cannot create lambda key for lambdaAssignmentId=" + run.lambdaAssignmentId +
                    ", invocationLane=" + run.invocationLane);
        }
        lambdaInput.apiKey = lambdaKey.key;
        lambdaInput.apiKeyExpiry = lambdaKey.expiry;

        // api hosts
        lambdaInput.apiHosts = PropertyStore.getList("vf.host.lambdas");

        request.appInput = StringMapper.toString(lambdaInput);
        return BytesMapper.writeValue(request);
    }

    public void resubmitInvokeRequest(byte[] request) {
        producer.sendAndAwait(Topics.LAMBDA_INVOKE_REQUEST, request);
    }

    /**
     * Submit the request only while it still represents the active asynchronous run.
     *
     * @return {@code true} when Lambda accepted the request; {@code false} when the run is stale or was already
     * submitted
     */
    public boolean sendAsyncRequest(LambdaInvokeRequest request) {
        LambdaRun run = lambdaRunDao.getLambdaRun(request.lambdaAssignmentId, InvocationLane.ASYNC);
        if ((run == null) || (run.runId != request.requestId) || run.started || (run.awsRequestId != null)) {
            logger.debug("Skip stale or already submitted asynchronous request {}", request);
            return false;
        }

        String awsRequestId = lambdaFunctionInvoker.invokeAsync(request.lambdaFunction, request.appInput);
        if ((awsRequestId != null) &&
            !lambdaRunDao.updateLambdaRunSent(request.lambdaAssignmentId, request.requestId, awsRequestId))
        {
            logger.warn("Asynchronous request was accepted after its run changed: {}", request.toLogString());
        }
        logger.debug("Submitted asynchronous request {}", request);
        return true;
    }

    public void logResult(LambdaInvokeRequest result) {
        insertRunInfo(result.toLambdaRunInfo());
        if (LambdaRunInfo.isLambdaError(result.resultCode)) {
            LambdaError lambdaError = new LambdaError(result.lambdaAssignmentId, result.lane, result.eventTime, result.resultMessage);
            MessageDispatcher.sendLambdaError(lambdaError);
        } else if (result.resultCode == LambdaRunInfo.LAMBDA_NOT_STARTED) {
            logger.error("Lambda not started or failed to call the API: " +
                "locationId=" + result.locationId +
                ", lambdaAssignmentId=" + result.lambdaAssignmentId + ", lambdaVersionId=" + result.lambdaVersionId +
                (result.lane != InvocationLane.DEFAULT ? ", lane=" + result.lane : "") + ", function=" + result.lambdaFunction);
        }

        if (result.resultCode == LambdaRunInfo.LAMBDA_TIMEOUT) {
            String lambdaKey = Integer.toString(result.lambdaId) + '-' + (result.publicVersion ? 1 : 0);
            rateMonitors.computeIfAbsent(lambdaKey, _ -> createTimeoutRateMonitor(result.lambdaId, result.publicVersion)).addEvent();
        }
    }

    private EventRateMonitor createTimeoutRateMonitor(int lambdaId, boolean published) {
        long timeWindow = ONE_HOUR_MILLIS;
        Runnable action = () -> {
            logger.error("Too many timeouts, lambdaId=" + lambdaId + ", published=" + published);
//            lambdaDevelopmentService.rejectLambdaVersion(lambdaId, published, System.currentTimeMillis() - timeWindow,
//            String.format("Lambda call timeout frequency exceeds the limit: %d per hour", LambdaRun.getMaxTimeoutsPerHour()));
            logger.warn("Lambda call timeout frequency exceeds the limit: %d per hour".formatted(LambdaRun.getMaxTimeoutsPerHour()));
        };
        return new EventRateMonitor(timeWindow, 12, 1, new EventRateTrigger(LambdaRun::getMaxTimeoutsPerHour, action, ONE_HOUR_MILLIS));
    }

    private volatile ConcurrentLinkedQueue<LambdaRunInfo> batch = new ConcurrentLinkedQueue<>();
    private final AtomicInteger batchSize = new AtomicInteger();
    private volatile long lastBatchTime = System.currentTimeMillis();
    private final Object batchLock = new Object();

    private void insertRunInfo(LambdaRunInfo lambdaRunInfo) {
        boolean success = lambdaRunInfo.resultCode == 0;

        if (lambdaRunInfo.lambdaId == 0) {
            logger.warn("lambdaId not set for " + lambdaRunInfo);
        }

        if (!success) {
            lambdaRunDao.insertRunInfo(lambdaRunInfo);
            return;
        }

        // batch insert
        lambdaRunInfo.prepareFields(logger);
        batch.add(lambdaRunInfo);
        int size = batchSize.incrementAndGet();

        long checkTime = System.currentTimeMillis() -
            PropertyStore.getDuration(DurationProp.LAMBDA_STATS_MAX_DELAY).toMillis();

        int maxBatchSize = PropertyStore.getInt(IntProp.LAMBDA_STATS_MAX_BATCH_SIZE);
        if ((lastBatchTime > checkTime) && (size < maxBatchSize)) return;

        ConcurrentLinkedQueue<LambdaRunInfo> execsToProcess;
        synchronized (batchLock) {
            size = batchSize.get();
            if ((lastBatchTime > checkTime) && (size < maxBatchSize)) return;

            lastBatchTime = System.currentTimeMillis();
            if (size == 0) return;

            execsToProcess = batch;
            batchSize.set(0);
            batch = new ConcurrentLinkedQueue<>();
        }

        lambdaRunDao.insertRunsInfo(execsToProcess);
    }

    public void processAsyncResult(LambdaInvokeRequest lambdaInvokeRequest, LambdaOutput lambdaOutput) {
        logger.debug(">processAsyncResult() {}", lambdaInvokeRequest);

        byte resultCode = lambdaOutput.resultCode();

        if (resultCode == LambdaRunInfo.DUPLICATE_REQUEST) {
            logger.debug("| processAsyncResult: duplicate request, requestId=" + lambdaInvokeRequest.requestId + ", lambdaAssignmentId=" + lambdaInvokeRequest.lambdaAssignmentId);
            return;
        }

        LambdaRunInfo info = new LambdaRunInfo(lambdaInvokeRequest.lambdaId, lambdaInvokeRequest.lambdaAssignmentId,
            InvocationLane.ASYNC, InvocationLaneSettings.forLane(InvocationLane.ASYNC).memorySize());
        info.lambdaVersionId = lambdaInvokeRequest.lambdaVersionId;
        info.functionName = lambdaInvokeRequest.lambdaFunction;
        info.requestDate = new Timestamp(lambdaInvokeRequest.requestId);
        info.processingTime = System.currentTimeMillis() - lambdaInvokeRequest.requestId;
        info.resultMessage = lambdaOutput.errorMessage;
        info.executionTime = lambdaOutput.duration();
        info.resultCode = resultCode;
        lambdaRunDao.insertRunInfo(info);

        if (LambdaRunInfo.isLambdaError(info.resultCode)) {
            LambdaRun run = lambdaRunDao.getLambdaRun(lambdaInvokeRequest.lambdaAssignmentId, lambdaInvokeRequest.lane);
            if ((run != null) && (lambdaInvokeRequest.requestId == run.runId)) {
                info.resultMessage = lambdaOutput.errorMessage +
                    "\nLogGroup: " + LambdaLogService.functionLogGroupNamePrefix + lambdaInvokeRequest.lambdaFunction.split(":", -1)[0] +
                    "\nLogStream: " + run.awsLogStream +
                    "\nawsRequestId=" + run.awsRequestId +
                    "\nlogStartTimeMs=" + run.triggeredAt.getTime() +
                    "\nlogStartTime=" + DateFormatUtils.printDateTime(run.triggeredAt.getTime()) +
                    "\nlogEndTimeMs=" + System.currentTimeMillis() +
                    "\nlogEndTime=" + DateFormatUtils.printDateTime(System.currentTimeMillis());
            }
            LambdaError lambdaError = new LambdaError(info.lambdaAssignmentId, InvocationLane.ASYNC, info.requestDate.getTime(), info.resultMessage);
            MessageDispatcher.sendLambdaError(lambdaError);
        }

        publishLambdaOutput(lambdaInvokeRequest, lambdaOutput);
        processRunCompletion(lambdaInvokeRequest.runContext());
        logger.debug("<processAsyncResult()");
    }

    public CompletableFuture<LambdaInvocationOutcome> executeDefaultLaneLambdaRequest(LambdaInvokeRequest request)
        throws RejectedExecutionException
    {
        if (request.lane != InvocationLane.DEFAULT) {
            throw new IllegalArgumentException("Only default-lane requests use synchronous invocation: " + request.toLogString());
        }

        // can be rejected with RejectedExecutionException
        CompletableFuture<Void> runFuture = CompletableFuture.runAsync(() -> executeRequest(request), requestExecutor);

        DefaultLaneLambdaInvocationAttempt attempt = new DefaultLaneLambdaInvocationAttempt(
            request, lambdaRunStateService, requestTimeoutScheduler, LambdaRun.lambdaStartTimeout().toMillis(), TimeUnit.MILLISECONDS);
        return attempt.coordinate(runFuture);
    }

    public void processDefaultLaneInvocationOutcome(LambdaInvocationOutcome outcome, Runnable acknowledge) {
        switch (outcome.action()) {
            case ACKNOWLEDGE_ONLY, RETRY_PERSISTED -> acknowledge.run();
            case FINAL_RESULT -> {
                LambdaInvokeRequest result = outcome.request();
                completionOutboxService.enqueue(result.runContext());
                acknowledge.run();
                logResult(result);
            }
        }
    }

    private void executeRequest(LambdaInvokeRequest request) {
        long startTime = System.currentTimeMillis();
        LambdaOutput lambdaOutput;
        try {
            lambdaOutput = lambdaFunctionInvoker.invoke(request.lambdaFunction, request.appInput);
        } catch (Exception e) {
            logger.info("lambdaAssignmentId=" + request.lambdaAssignmentId + " : " + e.getMessage());
            request.executionTime = System.currentTimeMillis() - startTime;
            request.resultCode = LambdaRunInfo.AWS_CLIENT_ERROR;

            String message = "Exception when calling lambda, lambdaAssignmentId=" + request.lambdaAssignmentId + ", locationId=" + request.locationId + ", functionName=" + request.lambdaFunction;
            if (request.canRetry()) {
                AwsExceptions.logAwsExceptionAsWarning(logger, e, message);
                request.shouldRetry = true;
            } else {
                AwsExceptions.logAwsExceptionAsError(logger, e, message);
                request.shouldRetry = false;
            }
            return;
        }

        long executionTime = lambdaOutput.duration();
        request.executionTime = executionTime > 0 ? executionTime : (System.currentTimeMillis() - startTime);
        request.shouldRetry = false;
        request.setResult(lambdaOutput);

        if (request.resultCode == LambdaRunInfo.DUPLICATE_REQUEST) {
            if (logger.isDebugEnabled()) {
                logger.debug("processRequest() duplicate " + request.toLogString() +
                    ", startCode=" + lambdaOutput.startCode + "\nerrorMessage=" + lambdaOutput.errorMessage);
            }
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("processRequest() " + request.toLogString() +
                ", resultCode=" + request.resultCode +
                ", startCode=" + lambdaOutput.startCode +
                    (request.resultMessage != null ? "\nresultMessage=" + request.resultMessage : "")
            );
        }

        publishLambdaOutput(request, lambdaOutput);
    }

    private void publishLambdaOutput(LambdaInvokeRequest request, LambdaOutput lambdaOutput) {
        if (request.logging) {
            List<LambdaLogEvent> logRecords = LambdaAssignmentLog.prepare(lambdaOutput.logEvents, lambdaOutput.startTime, lambdaOutput.endTime);
            if (logger.isDebugEnabled()) {
                logger.debug("Publish lambda assignment log record, lambdaAssignmentId=" + request.lambdaAssignmentId + ", functionName=" + request.lambdaFunction + ", eventCount=" + (logRecords.size() - 3));
            }
            LambdaAssignmentLog.publish(request.requestId, request.lambdaAssignmentId, request.lane, request.lambdaId, logRecords);
        }
    }
}
