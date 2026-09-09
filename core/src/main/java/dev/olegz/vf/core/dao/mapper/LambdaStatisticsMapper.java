package dev.olegz.vf.core.dao.mapper;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaStatistics;
import dev.olegz.vf.core.domain.lambdarun.*;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersion;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersionStatus;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Param;

public interface LambdaStatisticsMapper {
    void insertLambdaError(
        @Param("lambdaVersionId") int lambdaVersionId,
        @Param("functionStartDate") Timestamp functionStartDate,
        @Param("errorSignature") int errorSignature,
        @Param("errorText") String errorText,
        @Param("errorMessage") String errorMessage,
        @Param("date") Timestamp date);
    void updateLambdaErrorCount(
        @Param("lambdaVersionId") int lambdaVersionId,
        @Param("functionStartDate") Timestamp startDate,
        @Param("errorSignature") int errorSignature);
    boolean updateLambdaErrorDate(
        @Param("lambdaVersionId") int lambdaVersionId,
        @Param("functionStartDate") Timestamp functionStartDate,
        @Param("errorSignature") int errorSignature,
        @Param("errorDate") Timestamp errorDate,
        @Param("date") Timestamp date);
    LambdaErrorAlert selectLambdaError(
        @Param("lambdaVersionId") int lambdaVersionId,
        @Param("functionStartDate") Timestamp functionStartDate,
        @Param("errorSignature") int errorSignature);
    void deleteLambdaErrors(int lambdaVersionId);
    void insertLambdaAssignmentRun(LambdaRun run);
    void updateLambdaAssignmentRunLogEndDate(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("lane") InvocationLane lane,
        @Param("logEndDate") Datetime logEndDate);
    Timestamp selectLambdaAssignmentRunLogEndDate(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("lane") InvocationLane lane);
    int countLambdaRuns(
        @Param("lambdaVersionId") int lambdaVersionId,
        @Param("lambdaAssignmentId") Integer lambdaAssignmentId,
        @Param("lane") InvocationLane lane,
        @Param("trigger") Integer trigger,
        @Param("errors") boolean errors,
        @Param("startDate") Timestamp startDate,
        @Param("endDate") Timestamp endDate,
        @Param("parts") int[] parts);
    List<LambdaRunInfo> selectLambdaRunsInfo(
        @Param("lambdaVersionId") int lambdaVersionId,
        @Param("lambdaAssignmentId") Integer lambdaAssignmentId,
        @Param("lane") InvocationLane lane,
        @Param("trigger") Integer trigger,
        @Param("errors") boolean errors,
        @Param("startDate") Timestamp startDate,
        @Param("endDate") Timestamp endDate,
        @Param("parts") int[] parts);
    @MapKey("lambdaVersionId")
    Map<Integer, LambdaStatistics> selectLambdaRunStatsDay(List<Integer> lambdaVersionIds);
    Timestamp selectMaxDateInExecStatDay();
    void insertLambdaExecStatDayAsSelect(
        @Param("part") int part,
        @Param("startDate") Datetime startDate,
        @Param("endDate") Datetime endDate);
    List<LambdaDailyMetrics> selectRunsDay(
        @Param("part") int part,
        @Param("startDate") Datetime startDate,
        @Param("endDate") Datetime endDate);
    void insertDailyMetrics(LambdaDailyMetrics metrics);
    List<LambdaDailyMetrics> selectDailyMetrics(
        @Param("lambdaId") int lambdaId,
        @Param("startDate") Datetime startDate);
    List<LambdaVersion> selectLambdaVersions(
        @Param("lambdaId") int lambdaId,
        @Param("statuses") LambdaVersionStatus[] statuses,
        @Param("version") String version);
}
