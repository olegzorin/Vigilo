package dev.olegz.vf.core.dao;

import java.sql.Timestamp;
import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaAssignment;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaStatistics;
import dev.olegz.vf.core.domain.lambdarun.LambdaDailyMetrics;
import dev.olegz.vf.core.domain.lambdarun.LambdaError;
import dev.olegz.vf.core.domain.lambdarun.LambdaErrorAlert;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;

public interface LambdaStatisticsDao {
    LambdaErrorAlert insertLambdaError(LambdaError lambdaError, LambdaAssignment lambdaAssignment);
    void deleteLambdaErrors(int lambdaVersionId);
    void updateLambdaRunLogEndDate(int lambdaAssignmentId, InvocationLane lane, Datetime endDate);
    Timestamp getLambdaAssignmentRunLogEndDate(int lambdaAssignmentId, InvocationLane lane);
    LambdaStatistics getExecutionHistory(int lambdaVersionId, Integer lambdaAssignmentId, InvocationLane lane, Integer trigger,
        boolean errors, Timestamp startDate, Timestamp endDate, boolean newestFirst, int maxCount);
    void loadStatistics(Lambda lambda);
    Timestamp getMaxStatDate();
    void aggregateLambdaStatistic(Datetime startDate, Datetime endDate);
    List<LambdaDailyMetrics> getRunsDay(Datetime startDate, Datetime endDate);
    void insertDailyMetrics(List<LambdaDailyMetrics> metrics);
    List<LambdaDailyMetrics> getDailyMetrics(int lambdaId, Datetime date);
}
