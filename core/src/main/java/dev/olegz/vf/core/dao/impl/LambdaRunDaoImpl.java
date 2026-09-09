package dev.olegz.vf.core.dao.impl;

import java.sql.Timestamp;
import java.util.*;

import dev.olegz.vf.registry.cache.CacheNames;
import dev.olegz.vf.core.dao.LambdaAsyncSubmissionOutboxDao;
import dev.olegz.vf.core.dao.LambdaInvokeRetryOutboxDao;
import dev.olegz.vf.core.dao.LambdaRunCompletionOutboxDao;
import dev.olegz.vf.core.dao.LambdaRunDao;
import dev.olegz.vf.core.dao.mapper.LambdaRunMapper;
import dev.olegz.vf.core.domain.lambdarun.*;
import dev.olegz.vf.core.domain.lambdarun.input.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;

@Repository
public class LambdaRunDaoImpl implements LambdaRunDao {
    private static final Logger logger = LoggerFactory.getLogger(LambdaRunDaoImpl.class);

    private final LambdaRunMapper mapper;
    private final LambdaRuntimeAssignmentCache runtimeAssignmentCache;
    private final LambdaAsyncSubmissionOutboxDao asyncSubmissionOutboxDao;
    private final LambdaInvokeRetryOutboxDao retryOutboxDao;
    private final LambdaRunCompletionOutboxDao completionOutboxDao;

    public LambdaRunDaoImpl(
        LambdaRunMapper mapper,
        LambdaRuntimeAssignmentCache runtimeAssignmentCache,
        LambdaAsyncSubmissionOutboxDao asyncSubmissionOutboxDao,
        LambdaInvokeRetryOutboxDao retryOutboxDao,
        LambdaRunCompletionOutboxDao completionOutboxDao)
    {
        this.mapper = mapper;
        this.runtimeAssignmentCache = runtimeAssignmentCache;
        this.asyncSubmissionOutboxDao = asyncSubmissionOutboxDao;
        this.retryOutboxDao = retryOutboxDao;
        this.completionOutboxDao = completionOutboxDao;
    }

    private static Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }

    @Override
    public List<ScheduledLambdaAssignment> getScheduledLambdaAssignments(Timestamp date) {
        return mapper.selectScheduledLambdaAssignments(date);
    }

    @Override
    public void updateScheduleDates(int lambdaAssignmentId, Timestamp lastDate, Timestamp nextDate) {
        mapper.updateScheduleDates(lambdaAssignmentId, lastDate, nextDate);
    }

    @Override
    public boolean updateScheduleDatesIfUnchanged(
        int lambdaAssignmentId,
        Timestamp expectedLastDate,
        Timestamp expectedNextDate,
        Timestamp lastDate,
        Timestamp nextDate)
    {
        return mapper.updateScheduleDatesIfUnchanged(
            lambdaAssignmentId, expectedLastDate, expectedNextDate, lastDate, nextDate);
    }

    @Override
    public List<LambdaRuntimeAssignment> getRuntimeAssignmentsForTrigger(int locationId, int trigger) {
        return filterRuntimeAssignments(
            runtimeAssignmentCache.getForLocation(locationId), trigger, System.currentTimeMillis());
    }

    static List<LambdaRuntimeAssignment> filterRuntimeAssignments(
        List<LambdaRuntimeAssignmentCache.Snapshot> snapshots,
        int trigger,
        long currentTime)
    {
        if ((snapshots == null) || snapshots.isEmpty()) return List.of();

        List<LambdaRuntimeAssignment> assignments = new ArrayList<>(snapshots.size());
        for (LambdaRuntimeAssignmentCache.Snapshot snapshot : snapshots) {
            if ((snapshot != null) && snapshot.isActiveFor(trigger, currentTime)) {
                assignments.add(snapshot.toAssignment());
            }
        }
        return assignments;
    }

    @Override
    public LambdaRuntimeAssignment getActiveLambdaRuntimeAssignment(int lambdaAssignmentId) {
        return mapper.selectActiveLambdaRuntimeAssignmentById(lambdaAssignmentId, now());
    }

    @Override
    @Cacheable(cacheNames = CacheNames.TRIGGER_LOCATION_METADATA)
    public LocationMetadataSnapshot getTriggerLocationMetadata(int locationId) {
        return mapper.selectTriggerLocationMetadata(locationId);
    }

    @Override
    @Cacheable(cacheNames = CacheNames.TRIGGER_LOCATION_DEVICE_METADATA)
    public List<LocationDeviceMetadataSnapshot> getTriggerLocationDeviceMetadata(int locationId) {
        List<LocationDeviceMetadataSnapshot> metadata =
            mapper.selectTriggerLocationDeviceMetadata(locationId);
        return metadata != null ? List.copyOf(metadata) : List.of();
    }

    @Override
    public LocationHydrationSnapshot getTriggerLocationHydration(int locationId) {
        return toLocationHydrationSnapshot(mapper.selectTriggerLocationHydration(locationId, now()));
    }

    static LocationHydrationSnapshot toLocationHydrationSnapshot(List<LocationHydrationRow> rows) {
        if ((rows == null) || rows.isEmpty()) {
            return new LocationHydrationSnapshot(null, List.of(), List.of());
        }

        String currentState = null;
        List<LocationDeviceStateSnapshot> deviceStates = new ArrayList<>();
        List<LocationUserSnapshot> users = new ArrayList<>();
        for (LocationHydrationRow row : rows) {
            if (row == null) continue;

            switch (row.rowType) {
                case 0 -> currentState = row.locationCurrentState;
                case 1 -> {
                    if (row.deviceUuid == null) continue;

                    var state = row.deviceCurrentState != null ?
                        Collections.unmodifiableMap(new LinkedHashMap<>(row.deviceCurrentState)) : null;
                    deviceStates.add(new LocationDeviceStateSnapshot(row.deviceUuid, state));
                }
                case 2 -> {
                    if (row.userId == null) continue;

                    LocationUserSnapshot user = new LocationUserSnapshot();
                    user.userId = row.userId;
                    user.locationAccess = row.locationAccess != null ? row.locationAccess : 0;
                    users.add(user);
                }
                default -> logger.warn("Unknown trigger location hydration row type {}", row.rowType);
            }
        }
        return new LocationHydrationSnapshot(
            currentState, List.copyOf(deviceStates), List.copyOf(users));
    }

    @Override
    public boolean checkLambdaDeviceAccess(String deviceId, int locationId) {
        return mapper.checkLambdaDeviceAccess(deviceId, locationId);
    }

    @Override
    public List<LambdaRun> getLambdaRunsRequiringCompletion() {
        return mapper.selectLambdaRunsRequiringCompletion(
            new Timestamp(System.currentTimeMillis() - LambdaRun.executionExpiryGracePeriod().toMillis()));
    }

    @Override
    public List<LambdaRun> getAsyncLambdaRunsRequiringCompletion() {
        return mapper.selectAsyncLambdaRunsRequiringCompletion(
            new Timestamp(System.currentTimeMillis() - LambdaRun.executionExpiryGracePeriod().toMillis()));
    }

    @Override
    public LambdaRun getLambdaRun(int lambdaAssignmentId, InvocationLane lane) {
        return mapper.selectLambdaRun(lambdaAssignmentId, lane);
    }

    @Override
    public LambdaRun getLambdaRunByAwsRequestId(String awsRequestId) {
        return mapper.selectLambdaRunByAwsRequestId(awsRequestId);
    }

    @Override
    public boolean updateLambdaRunSent(int lambdaAssignmentId, long runId, String awsRequestId) {
        return mapper.updateLambdaRunSubmitAsync(lambdaAssignmentId, runId, awsRequestId);
    }

    @Override
    public boolean updateLambdaRunStarted(int lambdaAssignmentId, InvocationLane lane, long runId, int invocationGen,
        String awsRequestId, String awsLogStream)
    {
        if (lane != InvocationLane.ASYNC) {
            awsRequestId = null;
            awsLogStream = null;
        }
        return mapper.updateLambdaRunAsStarted(lambdaAssignmentId, lane, now(), awsRequestId, awsLogStream, runId, invocationGen);
    }

    @Override
    public void deleteLambdaAssignmentRunData(int lambdaAssignmentId) {
        asyncSubmissionOutboxDao.deleteForLambdaAssignment(lambdaAssignmentId);
        retryOutboxDao.deleteForLambdaAssignment(lambdaAssignmentId);
        completionOutboxDao.deleteForLambdaAssignment(lambdaAssignmentId);
        final int limit = 100;
        int count;
        do {
            count = mapper.deleteLambdaPendingInputs(lambdaAssignmentId, limit);
        } while (count == limit);
        mapper.deleteLambdaAssignmentRun(lambdaAssignmentId);
    }

    @Override
    public void insertRunInfo(LambdaRunInfo runInfo) {
        runInfo.prepareFields(logger);
        mapper.insertLambdaRunsInfo(List.of(runInfo));
    }

    @Override
    public void insertRunsInfo(Collection<LambdaRunInfo> runs) {
        mapper.insertLambdaRunsInfo(runs);
    }
}
