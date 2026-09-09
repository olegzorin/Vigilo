package dev.olegz.vf.core.dao.mapper;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.core.dao.metric.LambdaWorkloadMetric;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaVariable;
import dev.olegz.vf.core.domain.lambdarun.*;
import dev.olegz.vf.core.domain.lambdarun.input.LocationDeviceMetadataSnapshot;
import dev.olegz.vf.core.domain.lambdarun.input.LocationHydrationRow;
import dev.olegz.vf.core.domain.lambdarun.input.LocationMetadataSnapshot;
import org.apache.ibatis.annotations.Param;
import static dev.olegz.vf.core.dao.metric.LambdaWorkloadMetric.Operation.*;

public interface LambdaRunMapper {
    @LambdaWorkloadMetric(operation = sqlSelectScheduledLambdaAssignments)
    List<ScheduledLambdaAssignment> selectScheduledLambdaAssignments(Timestamp date);
    void updateScheduleDates(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("lastDate") Timestamp lastDate,
        @Param("nextDate") Timestamp nextDate);
    boolean updateScheduleDatesIfUnchanged(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("expectedLastDate") Timestamp expectedLastDate,
        @Param("expectedNextDate") Timestamp expectedNextDate,
        @Param("lastDate") Timestamp lastDate,
        @Param("nextDate") Timestamp nextDate);
    @LambdaWorkloadMetric(operation = sqlGetActiveLambdaRuntimeAssignmentById)
    LambdaRuntimeAssignment selectActiveLambdaRuntimeAssignmentById(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("date") Timestamp date);
    @LambdaWorkloadMetric(operation = sqlSelectRuntimeAssignmentsForLocation)
    List<LambdaRuntimeAssignment> selectRuntimeAssignmentsForLocation(
        @Param("locationId") int locationId);

    @LambdaWorkloadMetric(operation = sqlSelectTriggerLocationMetadata)
    LocationMetadataSnapshot selectTriggerLocationMetadata(int locationId);

    @LambdaWorkloadMetric(operation = sqlSelectTriggerLocationDeviceMetadata)
    List<LocationDeviceMetadataSnapshot> selectTriggerLocationDeviceMetadata(int locationId);

    @LambdaWorkloadMetric(operation = sqlSelectTriggerLocationHydration)
    List<LocationHydrationRow> selectTriggerLocationHydration(
        @Param("locationId") int locationId,
        @Param("date") Timestamp date);

    boolean checkLambdaDeviceAccess(
        @Param("deviceId") String deviceId,
        @Param("locationId") int locationId);

    @LambdaWorkloadMetric(operation = sqlInsertLambdaRunsInfo)
    void insertLambdaRunsInfo(Collection<LambdaRunInfo> runs);

    @LambdaWorkloadMetric(operation = sqlUpdateLambdaPendingInput)
    boolean updateLambdaPendingInput(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("lane") InvocationLane lane,
        @Param("input") LambdaPendingInputRecord input,
        @Param("large") boolean large,
        @Param("paddedSize") int paddedSize,
        @Param("nextInputNumber") long nextInputNumber,
        @Param("part") int part);

    @LambdaWorkloadMetric(operation = sqlInsertLambdaPendingInput)
    void insertLambdaPendingInput(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("lane") InvocationLane lane,
        @Param("input") LambdaPendingInputRecord input,
        @Param("large") boolean large,
        @Param("paddedSize") int paddedSize,
        @Param("part") int part);

    @LambdaWorkloadMetric(operation = sqlSelectLambdaPendingInputs)
    List<LambdaPendingInputRecord> selectLambdaPendingInputs(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("lane") InvocationLane lane,
        @Param("nextInputNumber") long nextInputNumber,
        @Param("maxCount") int maxCountCount,
        @Param("sorted") boolean sorted,
        @Param("partitions") String partitions);

    LambdaRun selectLambdaRunByAwsRequestId(String awsRequestId);

    @LambdaWorkloadMetric(operation = sqlSelectLambdaRunForUpdate)
    LambdaRun selectLambdaRunForUpdate(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("lane") InvocationLane lane);

    @LambdaWorkloadMetric(operation = sqlSelectLambdaRun)
    LambdaRun selectLambdaRun(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("lane") InvocationLane lane);

    void insertLambdaRun(LambdaRun lambdaRun);

    boolean updateLambdaRun(LambdaRun lambdaRun);

    @LambdaWorkloadMetric(operation = sqlUpdateLambdaRunPendingCount)
    void updateLambdaRunPendingCount(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("lane") InvocationLane lane,
        @Param("pendingCount") int pendingCount,
        @Param("nextInputNumber") long nextInputNumber);

    @LambdaWorkloadMetric(operation = sqlUpdateLambdaRunAsStarted)
    boolean updateLambdaRunAsStarted(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("lane") InvocationLane lane,
        @Param("startDate") Timestamp startDate,
        @Param("awsRequestId") String awsRequestId,
        @Param("awsLogStream") String awsLogStream,
        @Param("runId") long runId,
        @Param("invocationGen") int invocationGen);

    void updateLambdaRunAsNotStarted(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("lane") InvocationLane lane
    );
    void updateLambdaRunForRetry(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("lane") InvocationLane lane,
        @Param("invocationGen") int invocationGen,
        @Param("expiryDate") Timestamp expiryDate);

    @LambdaWorkloadMetric(operation = sqlSelectLambdaRunsRequiringCompletion)
    List<LambdaRun> selectLambdaRunsRequiringCompletion(Timestamp expiryDate);
    @LambdaWorkloadMetric(operation = sqlSelectAsyncLambdaRunsRequiringCompletion)
    List<LambdaRun> selectAsyncLambdaRunsRequiringCompletion(Timestamp expiryDate);

    @LambdaWorkloadMetric(operation = sqlUpdateLambdaRunSubmitAsync)
    boolean updateLambdaRunSubmitAsync(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("runId") long runId,
        @Param("awsRequestId") String awsRequestId);

    @LambdaWorkloadMetric(operation = sqlCompleteLambdaRun)
    boolean updateLambdaRunAsComplete(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("lane") InvocationLane lane,
        @Param("prevRunId") Long prevRunId);

    int deleteLambdaPendingInputs(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("limit") int limit);

    void deleteLambdaAssignmentRun(int lambdaAssignmentId);

    @LambdaWorkloadMetric(operation = sqlSelectLambdaVariables)
    LambdaVariable selectLambdaAssignmentVariable(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("generation") long generation,
        @Param("name") String name);

    @LambdaWorkloadMetric(operation = sqlDeleteLambdaVariable)
    void deleteLambdaAssignmentVariable(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("generation") Long generation,
        @Param("name") String name);

    @LambdaWorkloadMetric(operation = sqlInsertLambdaVariable)
    boolean insertLambdaAssignmentVariable(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("generation") long generation,
        @Param("name") String name,
        @Param("updateDate") Timestamp updateDate,
        @Param("originalSize") int originalSize,
        @Param("smallValue") byte[] smallValue,
        @Param("largeValue") byte[] largeValue);

    @LambdaWorkloadMetric(operation = sqlUpdateLambdaVariable)
    boolean updateLambdaAssignmentVariable(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("generation") long generation,
        @Param("name") String name,
        @Param("updateDate") Timestamp updateDate,
        @Param("originalSize") int originalSize,
        @Param("smallValue") byte[] smallValue,
        @Param("largeValue") byte[] largeValue);

    long selectLambdaAssignmentVariableGeneration(int lambdaAssignmentId);

    void advanceLambdaAssignmentVariableGeneration(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("generation") long generation);

    List<Integer> selectInactiveAssignmentVariables(
        @Param("date") Datetime date,
        @Param("maxCount") int maxCount);

    @LambdaWorkloadMetric(operation = sqlSelectLambdaVariables)
    LambdaVariable selectLambdaLocationVariable(
        @Param("locationId") int locationId,
        @Param("name") String name);

    @LambdaWorkloadMetric(operation = sqlDeleteLambdaVariable)
    void deleteLambdaLocationVariables(
        @Param("locationId") int locationId,
        @Param("name") String name);

    @LambdaWorkloadMetric(operation = sqlInsertLambdaVariable)
    void insertLambdaLocationVariable(
        @Param("locationId") int locationId,
        @Param("name") String name,
        @Param("updateDate") Timestamp updateDate,
        @Param("originalSize") int originalSize,
        @Param("smallValue") byte[] smallValue);

    @LambdaWorkloadMetric(operation = sqlUpdateLambdaVariable)
    boolean updateLambdaLocationVariable(
        @Param("locationId") int locationId,
        @Param("name") String name,
        @Param("updateDate") Timestamp updateDate,
        @Param("originalSize") int originalSize,
        @Param("smallValue") byte[] smallValue);

    List<Integer> selectInactiveLocationVariables(
        @Param("date") Datetime date,
        @Param("maxCount") int maxCount);
}
