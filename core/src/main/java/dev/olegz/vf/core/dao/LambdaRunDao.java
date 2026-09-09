package dev.olegz.vf.core.dao;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;

import dev.olegz.vf.core.domain.lambdarun.*;
import dev.olegz.vf.core.domain.lambdarun.input.LocationDeviceMetadataSnapshot;
import dev.olegz.vf.core.domain.lambdarun.input.LocationHydrationSnapshot;
import dev.olegz.vf.core.domain.lambdarun.input.LocationMetadataSnapshot;

public interface LambdaRunDao {
    List<ScheduledLambdaAssignment> getScheduledLambdaAssignments(Timestamp date);
    void updateScheduleDates(int lambdaAssignmentId, Timestamp lastDate, Timestamp nextDate);
    boolean updateScheduleDatesIfUnchanged(
        int lambdaAssignmentId,
        Timestamp expectedLastDate,
        Timestamp expectedNextDate,
        Timestamp lastDate,
        Timestamp nextDate);

    // trigger
    List<LambdaRuntimeAssignment> getRuntimeAssignmentsForTrigger(int locationId, int trigger);

    LambdaRuntimeAssignment getActiveLambdaRuntimeAssignment(int lambdaAssignmentId);

    LocationMetadataSnapshot getTriggerLocationMetadata(int locationId);

    List<LocationDeviceMetadataSnapshot> getTriggerLocationDeviceMetadata(int locationId);

    LocationHydrationSnapshot getTriggerLocationHydration(int locationId);

    List<LambdaRun> getLambdaRunsRequiringCompletion();
    List<LambdaRun> getAsyncLambdaRunsRequiringCompletion();

    boolean checkLambdaDeviceAccess(String deviceId, int locationId);

    void insertRunInfo(LambdaRunInfo runInfo);

    LambdaRun getLambdaRun(int lambdaAssignmentId, InvocationLane lane);
    LambdaRun getLambdaRunByAwsRequestId(String awsRequestId);
    boolean updateLambdaRunSent(int lambdaAssignmentId, long runId, String awsRequestId);

    boolean updateLambdaRunStarted(int lambdaAssignmentId, InvocationLane lane, long runId, int invocationGen, String awsRequestId, String awsLogStream);

    void deleteLambdaAssignmentRunData(int lambdaAssignmentId);

    void insertRunsInfo(Collection<LambdaRunInfo> runs);
}
