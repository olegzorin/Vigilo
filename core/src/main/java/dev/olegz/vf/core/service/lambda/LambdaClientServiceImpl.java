package dev.olegz.vf.core.service.lambda;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.common.exception.InvalidJwtException;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.common.exception.WrongParameterValueException;
import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.common.objectmap.StringMapper;
import dev.olegz.vf.common.props.DurationProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.common.schedule.CronExpression;
import dev.olegz.vf.core.dao.*;
import dev.olegz.vf.registry.dao.DeviceDao;
import dev.olegz.vf.registry.dao.LocationDao;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.domain.account.Location;
import dev.olegz.vf.registry.domain.account.LocationCurrentState;
import dev.olegz.vf.registry.domain.account.Organization;
import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaAssignment;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaVariable;
import dev.olegz.vf.core.domain.lambdarun.*;
import dev.olegz.vf.registry.domain.device.DeviceCurrentState;
import dev.olegz.vf.core.event.ResetEvent;
import dev.olegz.vf.core.event.ScheduledEvent;
import dev.olegz.vf.core.event.TriggerEvent;
import dev.olegz.vf.registry.service.encryption.JwtService;
import dev.olegz.vf.registry.service.encryption.SigningAlgorithm;
import dev.olegz.vf.messaging.ConfirmingKeyedMessageProducer;
import dev.olegz.vf.messaging.Messaging;
import dev.olegz.vf.messaging.MessagingProvider;
import dev.olegz.vf.messaging.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("lambdaClientService")
public class LambdaClientServiceImpl implements LambdaClientService {
    private static final Logger logger = LoggerFactory.getLogger(LambdaClientServiceImpl.class);

    private final LambdaRunDao lambdaRunDao;
    private final LambdaVariableDao lambdaVariableDao;
    private final LocationDao locationDao;
    private final DeviceDao deviceDao;
    private final OrganizationDao organizationDao;
    private final JwtService jwtService;
    private final ConfirmingKeyedMessageProducer producer;
    private final LambdaRunCompletionOutboxService completionOutboxService;
    private final LambdaResetOutboxService resetOutboxService;

    @Autowired
    public LambdaClientServiceImpl(
        LambdaRunDao lambdaRunDao,
        LambdaVariableDao lambdaVariableDao,
        LocationDao locationDao,
        DeviceDao deviceDao,
        OrganizationDao organizationDao,
        JwtService jwtService,
        LambdaRunCompletionOutboxService completionOutboxService,
        LambdaResetOutboxService resetOutboxService)
    {
        this(
            lambdaRunDao,
            lambdaVariableDao,
            locationDao,
            deviceDao,
            organizationDao,
            jwtService,
            completionOutboxService,
            resetOutboxService,
            Messaging.producer(MessagingProvider.KAFKA, "lambda", ConfirmingKeyedMessageProducer.class));
    }

    LambdaClientServiceImpl(
        LambdaRunDao lambdaRunDao,
        LambdaVariableDao lambdaVariableDao,
        LocationDao locationDao,
        DeviceDao deviceDao,
        OrganizationDao organizationDao,
        JwtService jwtService,
        ConfirmingKeyedMessageProducer producer)
    {
        this(lambdaRunDao, lambdaVariableDao, locationDao, deviceDao, organizationDao, jwtService, null, null, producer);
    }

    LambdaClientServiceImpl(
        LambdaRunDao lambdaRunDao,
        LambdaVariableDao lambdaVariableDao,
        LocationDao locationDao,
        DeviceDao deviceDao,
        OrganizationDao organizationDao,
        JwtService jwtService,
        LambdaRunCompletionOutboxService completionOutboxService,
        LambdaResetOutboxService resetOutboxService,
        ConfirmingKeyedMessageProducer producer)
    {
        this.lambdaRunDao = lambdaRunDao;
        this.lambdaVariableDao = lambdaVariableDao;
        this.locationDao = locationDao;
        this.deviceDao = deviceDao;
        this.organizationDao = organizationDao;
        this.jwtService = jwtService;
        this.completionOutboxService = completionOutboxService;
        this.resetOutboxService = resetOutboxService;
        this.producer = producer;
    }

    @Override
    public LambdaKeyInput createLambdaKey(LambdaRuntimeAssignment lambda, long expiry, InvocationLane lane, int triggers) {
        try {
            long variableGeneration =
                lambdaVariableDao.getLambdaAssignmentVariableGeneration(lambda.lambdaAssignmentId);
            LambdaKeyJwtClaims claims =
                new LambdaKeyJwtClaims(lambda, expiry, lane, triggers, variableGeneration);
            String jwt = jwtService.createJwt(claims, SigningAlgorithm.HS512);
            return new LambdaKeyInput(jwt, claims.exp);
        } catch (Exception e) {
            logger.error("Exception in creating JWT lambda key for lambdaAssignmentId=" + lambda.lambdaAssignmentId + ", lane=" + lane, e);
            return new LambdaKeyInput(null, 0);
        }
    }

    @Override
    public LambdaKeyInput generateLambdaApiKey(User user, int lambdaAssignmentId) {
        LambdaRuntimeAssignment lambda = lambdaRunDao.getActiveLambdaRuntimeAssignment(lambdaAssignmentId);
        if (lambda == null) {
            throw new ObjectNotFoundException("Lambda assignment not found or inactive");
        }

        Location userLocation = locationDao.getLocationByUser(user);
        boolean locationOwner = userLocation != null && userLocation.locationId == lambda.locationId;
        if (!locationOwner) {
            Organization organization = organizationDao.getOrganization(lambda.organizationId);
            boolean organizationAdmin = user.organizationId == lambda.organizationId &&
                organization != null && organization.adminUserId != null &&
                organization.adminUserId == user.userId;
            if (!organizationAdmin) {
                throw new AccessDeniedException("Lambda assignment is not accessible");
            }
        }

        LambdaKeyInput key = createLambdaKey(
            lambda,
            Math.toIntExact(PropertyStore.getDuration(DurationProp.LAMBDA_USER_KEY_EXPIRY).toSeconds()),
            InvocationLane.DEFAULT,
            0);
        if (key.key == null) {
            throw new ApplicationFailureException("Cannot generate lambda API key");
        }
        return key;
    }

    @Override
    public LambdaKey parseLambdaKey(String appKey) throws InvalidJwtException {
        if (appKey == null) throw new InvalidJwtException();

        LambdaKeyJwtClaims jwtClaims = jwtService.verifyJwt(appKey, LambdaKeyJwtClaims.class);

        return new LambdaKey(jwtClaims);
    }

    // Lambdas run methods

    @Override
    public boolean checkLambdaLocationAccess(LambdaKey key, int locationId) {
        return (key.locationId != 0) && (key.locationId == locationId);
    }

    @Override
    public boolean checkLambdaDeviceAccess(LambdaKey key, String deviceId) {
        return (deviceId != null) && (key.locationId != 0) &&
            lambdaRunDao.checkLambdaDeviceAccess(deviceId, key.locationId);
    }

    @Override
    @Transactional
    public LocationCurrentState updateLocationCurrentState(
        String appKey,
        int locationId,
        String state)
    {
        LambdaKey lambdaKey = parseLambdaKey(appKey);
        if (!checkLambdaLocationAccess(lambdaKey, locationId)) {
            throw new AccessDeniedException("Access to location " + locationId + " denied");
        }

        LocationCurrentState currentState = new LocationCurrentState();
        currentState.locationId = locationId;
        currentState.state = state;
        currentState.stateDate = Datetime.now();
        locationDao.saveLocationCurrentState(currentState);
        return currentState;
    }

    @Override
    @Transactional
    public DeviceCurrentState updateDeviceCurrentState(
        String appKey,
        String deviceId,
        Map<String, Object> state,
        Datetime measuredAt)
    {
        LambdaKey lambdaKey = parseLambdaKey(appKey);
        if (!checkLambdaDeviceAccess(lambdaKey, deviceId)) {
            throw new AccessDeniedException("Access to device " + deviceId + " denied");
        }

        DeviceCurrentState currentState = new DeviceCurrentState();
        currentState.deviceUuid = deviceId;
        currentState.state = state;
        currentState.measuredAt = measuredAt;
        currentState.receivedAt = Datetime.now();
        deviceDao.saveDeviceCurrentState(currentState);
        return currentState;
    }

    @Override
    public byte[] getVariable(String appKey, String name, boolean shared) {
        LambdaKey lambdaKey = variableLambdaKey(appKey, name, shared);
        return shared ?
            lambdaVariableDao.getLambdaLocationVariable(lambdaKey.locationId, name) :
            lambdaVariableDao.getLambdaAssignmentVariable(
                lambdaKey.lambdaAssignmentId, lambdaKey.variableGeneration, name);
    }

    @Override
    @Transactional
    public void putVariable(String appKey, String name, boolean shared, byte[] value) {
        LambdaKey lambdaKey = variableLambdaKey(appKey, name, shared);
        if (shared) {
            lambdaVariableDao.putLambdaLocationVariable(lambdaKey.locationId, name, value);
        } else {
            lambdaVariableDao.putLambdaAssignmentVariable(
                lambdaKey.lambdaAssignmentId, lambdaKey.variableGeneration, name, value);
        }
    }

    @Override
    @Transactional
    public void deleteVariable(String appKey, String name, boolean shared) {
        LambdaKey lambdaKey = variableLambdaKey(appKey, name, shared);
        if (shared) {
            lambdaVariableDao.deleteLambdaLocationVariable(lambdaKey.locationId, name);
        } else {
            lambdaVariableDao.deleteLambdaAssignmentVariable(
                lambdaKey.lambdaAssignmentId, lambdaKey.variableGeneration, name);
        }
    }

    private LambdaKey variableLambdaKey(String appKey, String name, boolean shared) {
        if ((name == null) || name.isBlank() || (name.length() > LambdaVariable.MAX_NAME_LENGTH)) {
            throw new WrongParameterValueException("Invalid lambda variable name");
        }

        LambdaKey lambdaKey = parseLambdaKey(appKey);
        if (shared && (lambdaKey.locationId == 0)) {
            throw new AccessDeniedException("Lambda has no location for shared variable access");
        }
        return lambdaKey;
    }

    // Client methods

    @Override
    public void sendResetEvent(LambdaAssignment lambdaAssignment) {
        ResetEvent event = new ResetEvent(lambdaAssignment.locationId, lambdaAssignment.lambdaAssignmentId);
        logger.debug("sendResetEvent() {}", event);

        event.eventId = UUID.randomUUID().toString();
        event.time = System.currentTimeMillis();
        event.variableGeneration = event.time;
        resetOutboxService.enqueue(event);
    }


    @Override
    public void publishTriggerEvent(TriggerEvent event) {
        logger.debug("publishTriggerEvent() {}", event);

        if ((event.eventId == null) || event.eventId.isBlank()) event.eventId = UUID.randomUUID().toString();
        if (event.time == 0L) event.time = System.currentTimeMillis();
        final byte[] msgBytes = BytesMapper.writeValue(event);
        if (event.locationId != 0) {
            producer.sendAndAwait(Topics.LAMBDA_INPUT, event.locationId, msgBytes);
        } else {
            producer.sendAndAwait(Topics.LAMBDA_INPUT, msgBytes);
        }
    }

    @Override
    public void dispatchExpiredLambdaRunCompletions() {
        enqueueExpiredLambdaRunCompletions(lambdaRunDao.getLambdaRunsRequiringCompletion());
        enqueueExpiredLambdaRunCompletions(lambdaRunDao.getAsyncLambdaRunsRequiringCompletion());
    }

    private void enqueueExpiredLambdaRunCompletions(List<LambdaRun> runs) {
        if (runs != null) {
            for (LambdaRun run : runs) {
                logger.debug("Enqueuing completion for expired lambda run {}", run);
                completionOutboxService.enqueue(run.toRunContext());
            }
        }
    }

    @Override
    public boolean startRun(LambdaKey lambdaKey, long invocationToken, String awsRequestId, String logStreamName) {
        long runId = LambdaRun.getRunIdFromInvocationToken(invocationToken);
        int invocationGen = LambdaRun.getInvocationGenFromToken(invocationToken);

        if (logger.isDebugEnabled()) {
            logger.debug("startRun() check " + lambdaKey + ", runId=" + runId + ", invocationGen=" + invocationGen +
                ", invocationToken=" + invocationToken +
                (awsRequestId != null ? "\nawsRequestId=" + awsRequestId : "") +
                (logStreamName != null ? ", logStreamName=" + logStreamName : ""));
        }

        if (lambdaRunDao.updateLambdaRunStarted(lambdaKey.lambdaAssignmentId, lambdaKey.lane, runId, invocationGen, awsRequestId, logStreamName)) {
            if (logger.isDebugEnabled()) {
                logger.debug("startRun() started " + lambdaKey + ", runId=" + runId + ", invocationGen=" + invocationGen);
            }
            return true;
        }

        LambdaRunInfo info = new LambdaRunInfo(lambdaKey.lambdaId, lambdaKey.lambdaAssignmentId, lambdaKey.lane, 0);
        info.server = ""; // TODO node index;
        info.resultCode = LambdaRunInfo.DUPLICATE_REQUEST;
        info.triggers = lambdaKey.triggers();
        info.requestDate = new Timestamp(runId);
        info.executionTime = (int) (System.currentTimeMillis() - runId);

        LambdaRun lambdaRun = lambdaRunDao.getLambdaRun(lambdaKey.lambdaAssignmentId, lambdaKey.lane);
        if (lambdaRun != null) {
            info.functionName = lambdaRun.functionName;
            StringBuilder buf = new StringBuilder(120);
            buf.append("Requested: {runId=").append(runId)
                .append(", invocationGen=").append(invocationGen)
                .append("}, actual: {");
            if (lambdaRun.runId != runId) buf.append("runId=").append(lambdaRun.runId);
            if (lambdaRun.invocationGen != invocationGen) buf.append(", invocationGen=").append(lambdaRun.invocationGen);
            if (lambdaRun.started) buf.append(", started");
            buf.append('}');
            info.resultMessage = buf.toString();
        }
        lambdaRunDao.insertRunInfo(info);

        if (logger.isInfoEnabled()) {
            logger.info("startRun() rejected " + lambdaKey + ", triggers=" + info.triggers + ", " + info.resultMessage);
        }
        return false;
    }

    @Override
    // Not reentrant: schedule parsing and cursor evaluation remain single-threaded.
    public void triggerScheduledLambdas(Consumer<ScheduledEvent> eventConsumer) {
        boolean debug = logger.isDebugEnabled();
        if (debug) logger.debug(">triggerScheduleLambdas()");

        long startTime = System.currentTimeMillis();
        Timestamp startDate = new Timestamp(startTime);

        List<ScheduledLambdaAssignment> lambdaAssignments = lambdaRunDao.getScheduledLambdaAssignments(startDate);
        if ((lambdaAssignments == null) || lambdaAssignments.isEmpty()) {
            if (debug) logger.debug("<triggerScheduleLambdas() no lambdas");
            return;
        }

        if (lambdaAssignments.size() > 1) {
            lambdaAssignments.sort(Comparator.comparingLong(lambda -> lambda.scheduleLastDate == null ? 0L : lambda.scheduleLastDate.getTime()));
        }

        long triggeringInterval = Math.max(0L,
            PropertyStore.getDuration(DurationProp.LAMBDA_SCHEDULE_TRIGGERING_INTERVAL).toMillis());
        Map<Integer, List<Runnable>> triggersByLambdaVersion = new LinkedHashMap<>();
        AtomicInteger triggered = new AtomicInteger();

        for (ScheduledLambdaAssignment lambdaAssignment : lambdaAssignments) {
            try {
                if (lambdaAssignment.scheduleLastDate == null) {
                    long nextTime = ActiveSchedule.newLambdasNextTime(lambdaAssignment, startTime);
                    if (nextTime != 0L) {
                        lambdaRunDao.updateScheduleDates(lambdaAssignment.lambdaAssignmentId, startDate, new Timestamp(nextTime));
                    }
                } else {
                    long time = System.currentTimeMillis();
                    ActiveSchedule activeSchedule = ActiveSchedule.from(lambdaAssignment, time);
                    if (activeSchedule == null) continue;

                    if (activeSchedule.scheduleIds != null) {
                        // lambda should be triggered now
                        ActiveSchedule scheduleToFire = activeSchedule;
                        triggersByLambdaVersion
                            .computeIfAbsent(lambdaAssignment.lambdaVersionId, _ -> new ArrayList<>())
                            .add(() -> {
                                try {
                                    long fireTime = System.currentTimeMillis();
                                    dispatchScheduledEvent(
                                        lambdaAssignment,
                                        scheduleToFire.scheduleIds,
                                        fireTime,
                                        eventConsumer);
                                    boolean advanced = lambdaRunDao.updateScheduleDatesIfUnchanged(
                                        lambdaAssignment.lambdaAssignmentId,
                                        lambdaAssignment.scheduleLastDate,
                                        lambdaAssignment.scheduleNextDate,
                                        new Timestamp(fireTime),
                                        new Timestamp(scheduleToFire.scheduleNextTime));
                                    if (advanced) {
                                        triggered.incrementAndGet();
                                    } else {
                                        logger.debug("Schedule cursor was already advanced for lambdaAssignmentId={}",
                                            lambdaAssignment.lambdaAssignmentId);
                                    }
                                } catch (Exception e) {
                                    logger.error("Exception in triggering scheduled lambda\n" + lambdaAssignment, e);
                                }
                            });
                    } else if ((lambdaAssignment.scheduleNextDate == null) || (lambdaAssignment.scheduleNextDate.getTime() < activeSchedule.scheduleNextTime)) {
                        // update schedule next dates for the lambdaAssignments which don't have up-to-date fire times
                        lambdaRunDao.updateScheduleDates(lambdaAssignment.lambdaAssignmentId, lambdaAssignment.scheduleLastDate, new Timestamp(activeSchedule.scheduleNextTime));
                    }
                }
            } catch (Exception e) {
                logger.error("Exception in scheduling lambda\n" + lambdaAssignment, e);
            }
        }

        try {
            fireIndependentlyByLambdaVersion(triggersByLambdaVersion, triggeringInterval);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            logger.error("Exception in triggering scheduled lambdas", e.getCause());
        }

        if (debug) {
            logger.debug("<triggerScheduleLambdas() selected=" + lambdaAssignments.size() + ", triggered=" + triggered.get() +
                ", duration=" + (System.currentTimeMillis() - startTime));
        }
    }

    static void fireIndependentlyByLambdaVersion(
        Map<Integer, List<Runnable>> triggersByLambdaVersion,
        long triggeringInterval)
        throws InterruptedException, ExecutionException
    {
        if (triggersByLambdaVersion.isEmpty()) return;

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<?>> futures = new ArrayList<>(triggersByLambdaVersion.size());
        try {
            for (List<Runnable> lambdaVersionTriggers : triggersByLambdaVersion.values()) {
                futures.add(executor.submit(() -> {
                    long finishTime = System.currentTimeMillis() + triggeringInterval;
                    for (int i = 0; i < lambdaVersionTriggers.size(); i++) {
                        lambdaVersionTriggers.get(i).run();

                        int remainingTriggers = lambdaVersionTriggers.size() - i - 1;
                        if (remainingTriggers > 0) {
                            long sleepTime = timeUntilNextFire(
                                finishTime,
                                System.currentTimeMillis(),
                                remainingTriggers);
                            if (sleepTime > 0L) Thread.sleep(sleepTime);
                        }
                    }
                    return null;
                }));
            }

            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    static long timeUntilNextFire(long finishTime, long currentTime, int remainingTriggers) {
        if (remainingTriggers <= 0) return 0L;
        return Math.max(0L, finishTime - currentTime) / remainingTriggers;
    }

    void dispatchScheduledEvent(
        ScheduledLambdaAssignment lambdaAssignment,
        List<String> scheduleIds,
        long time,
        Consumer<ScheduledEvent> eventConsumer)
    {
        ScheduledEvent event = new ScheduledEvent(
            lambdaAssignment.locationId,
            lambdaAssignment.lambdaAssignmentId,
            scheduleIds);
        event.eventId = scheduledEventId(lambdaAssignment, scheduleIds);
        event.time = time;
        logger.debug("dispatchScheduledEvent() {}", event);
        eventConsumer.accept(event);
    }

    private static String scheduledEventId(
        ScheduledLambdaAssignment lambdaAssignment,
        List<String> scheduleIds)
    {
        List<String> sortedScheduleIds = new ArrayList<>(scheduleIds);
        sortedScheduleIds.sort(String::compareTo);
        String identity = lambdaAssignment.lambdaAssignmentId + ":" +
            timestampMillis(lambdaAssignment.scheduleLastDate) + ":" +
            timestampMillis(lambdaAssignment.scheduleNextDate) + ":" +
            String.join(",", sortedScheduleIds);
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static long timestampMillis(Timestamp value) {
        return value == null ? 0L : value.getTime();
    }

    // !!! Non-concurrent !!!
    private static class ActiveSchedule {
        private static final HashMap<String, Map<String, String>> PARSED_SCHEDULES = new HashMap<>(32);
        private static final HashMap<String, CronExpression> CRON_EXPRESSIONS = new HashMap<>(32);

        // Schedule IDs that produce fire times after the app's scheduleLastDate but not after startTime
        private ArrayList<String> scheduleIds;
        // The earliest fire time among all schedule IDs' fire times which are after startTime
        private long scheduleNextTime;

        private static long newLambdasNextTime(ScheduledLambdaAssignment lambdaAssignment, long newLambdasTime) {
            Map<String, String> schedules = lambdaSchedules(lambdaAssignment);
            if (schedules == null) return 0;

            final TimeZone timeZone = TimeZone.getTimeZone(lambdaAssignment.timezone);
            long nextTime = 0;

            try {
                for (var schedule : schedules.entrySet()) {
                    String cronText = schedule.getValue();
                    CronExpression cronExpr = CRON_EXPRESSIONS.get(cronText);
                    if (cronExpr == null) {
                        cronExpr = new CronExpression(cronText);
                        CRON_EXPRESSIONS.put(cronText, cronExpr);
                    }

                    long triggerTime = cronExpr.getTimeAfter(newLambdasTime, timeZone);

                    if ((nextTime == 0) || (nextTime > triggerTime)) nextTime = triggerTime;
                }
            } catch (ParseException e) {
                logger.error("Exception in parsing lambda schedule\n" + lambdaAssignment + '\n' + e);
            }

            return nextTime;
        }

        private static ActiveSchedule from(ScheduledLambdaAssignment lambdaAssignment, long startTime)
            throws ParseException
        {
            Map<String, String> schedules = lambdaSchedules(lambdaAssignment);
            return schedules == null ? null : new ActiveSchedule(lambdaAssignment, schedules, startTime);
        }

        private static Map<String, String> lambdaSchedules(ScheduledLambdaAssignment lambdaAssignment) {
            Map<String, String> schedules = PARSED_SCHEDULES.get(lambdaAssignment.schedule);
            if (schedules == null) {
                try {
                    schedules = StringMapper.readStringMap(lambdaAssignment.schedule);
                } catch (Exception e) {
                    logger.error("Exception in reading lambda schedule\n" + lambdaAssignment + '\n' + e);
                    return null;
                }
                PARSED_SCHEDULES.put(lambdaAssignment.schedule, schedules);
            }
            return schedules.isEmpty() ? null : schedules;
        }

        private ActiveSchedule(ScheduledLambdaAssignment lambdaAssignment, Map<String, String> schedules, long startTime)
            throws ParseException
        {
            final long scheduleLastTime = lambdaAssignment.scheduleLastDate.getTime() + 1000L;
            final TimeZone timeZone = TimeZone.getTimeZone(lambdaAssignment.timezone);
            int lostFires = 0;

            for (var schedule : schedules.entrySet()) {
                String cronText = schedule.getValue();
                CronExpression cronExpr = CRON_EXPRESSIONS.get(cronText);
                if (cronExpr == null) {
                    cronExpr = new CronExpression(cronText);
                    CRON_EXPRESSIONS.put(cronText, cronExpr);
                }

                long triggerTime = cronExpr.getTimeAfter(scheduleLastTime, timeZone);

                if (triggerTime <= startTime) {
                    if (scheduleIds == null) scheduleIds = new ArrayList<>(schedules.size());

                    scheduleIds.add(schedule.getKey());
                    triggerTime = cronExpr.getTimeAfter(triggerTime + 1000L, timeZone);

                    while (triggerTime <= startTime) {
                        triggerTime = cronExpr.getTimeAfter(triggerTime + 1000L, timeZone);
                        lostFires++;
                    }
                }

                if ((scheduleNextTime == 0) || (scheduleNextTime > triggerTime)) scheduleNextTime = triggerTime;
            }

            if ((lostFires > 0) && logger.isInfoEnabled()) {
                // Most likely a lambda is scheduled to be triggered more frequently than once in the triggering interval (one hour)
                logger.info("Lost " + lostFires + " fire for " + lambdaAssignment);
            }
        }
    }
}
