package dev.olegz.vf.core.dao.impl;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.props.DurationProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.common.util.CollectionOps;
import dev.olegz.vf.core.dao.LambdaStatisticsDao;
import dev.olegz.vf.core.dao.mapper.LambdaStatisticsMapper;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaAssignment;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaStatistics;
import dev.olegz.vf.core.domain.lambdarun.*;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersionStatus;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class LambdaStatisticsDaoImpl implements LambdaStatisticsDao {
    private final LambdaStatisticsMapper mapper;

    public LambdaStatisticsDaoImpl(LambdaStatisticsMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public LambdaErrorAlert insertLambdaError(LambdaError lambdaError, LambdaAssignment lambdaAssignment) {
        String errorMessage = LambdaErrorSignature.getNormalizedError(lambdaError.message, 150);
        int errorSignature = lambdaError.signature != null ? lambdaError.signature :
            LambdaErrorSignature.getErrorSignature(errorMessage, lambdaAssignment.lambdaAssignmentId, lambdaAssignment.locationId);
        String errorText = PropertyStore.getBoolean("vf.lambda.storeErrorText", false) ?
            StringUtils.truncate(StringUtils.trimToNull(lambdaError.message), 16_000) : null;

        int lambdaVersionId = lambdaAssignment.lambdaVersionId;
        Timestamp startDate = lambdaAssignment.lambdaVersion.functionStartDate;
        Timestamp errorDate = new Timestamp(System.currentTimeMillis());
        try {
            mapper.insertLambdaError(lambdaVersionId, startDate, errorSignature, errorText, errorMessage, errorDate);
            return new LambdaErrorAlert(errorMessage, errorDate, 1);
        } catch (DuplicateKeyException ignore) {
            mapper.updateLambdaErrorCount(lambdaVersionId, startDate, errorSignature);
        }

        LambdaErrorAlert alert = mapper.selectLambdaError(lambdaVersionId, startDate, errorSignature);
        long silenceInterval = PropertyStore.getDuration(DurationProp.LAMBDA_ERROR_SILENCE_INTERVAL).toMillis();
        if (errorDate.getTime() - alert.errorDate.getTime() < silenceInterval) return null;
        if (mapper.updateLambdaErrorDate(lambdaVersionId, startDate, errorSignature, alert.errorDate, errorDate)) {
            alert.message = errorMessage;
            return alert;
        }
        return null;
    }

    @Override
    public void deleteLambdaErrors(int lambdaVersionId) {
        mapper.deleteLambdaErrors(lambdaVersionId);
    }

    @Override
    public void updateLambdaRunLogEndDate(int lambdaAssignmentId, InvocationLane lane, Datetime logEndDate) {
        if (lane != InvocationLane.DEFAULT) {
            try {
                var lambdaRun = new LambdaRun(lambdaAssignmentId, lane);
                lambdaRun.awsLogEndDate = logEndDate;
                mapper.insertLambdaAssignmentRun(lambdaRun);
                return;
            } catch (DuplicateKeyException ignore) {
            }
        }
        mapper.updateLambdaAssignmentRunLogEndDate(lambdaAssignmentId, lane, logEndDate);
    }

    @Override
    public Timestamp getLambdaAssignmentRunLogEndDate(int lambdaAssignmentId, InvocationLane lane) {
        return mapper.selectLambdaAssignmentRunLogEndDate(lambdaAssignmentId, lane);
    }

    @Override
    public LambdaStatistics getExecutionHistory(int lambdaVersionId, Integer lambdaAssignmentId, InvocationLane lane, Integer trigger,
        boolean errors, Timestamp startDate, Timestamp endDate, boolean newestFirst, int maxCount)
    {
        LambdaStatistics lambdaStatistics = new LambdaStatistics();
        int[] parts = LambdaRunInfo.partitions(startDate.getTime(), endDate.getTime());
        int count = mapper.countLambdaRuns(lambdaVersionId, lambdaAssignmentId, lane, trigger, errors, startDate, endDate, parts);
        if (count == 0) return lambdaStatistics;

        lambdaStatistics.totalRuns = count;
        int sortDirection = newestFirst ? -1 : 1;
        if (count <= maxCount) {
            lambdaStatistics.runsInfo = mapper.selectLambdaRunsInfo(lambdaVersionId, lambdaAssignmentId, lane, trigger, errors,
                startDate, endDate, parts);
            if (count > 1) {
                lambdaStatistics.runsInfo.sort(Comparator.comparingLong(r -> sortDirection * r.requestDate.getTime()));
            }
            return lambdaStatistics;
        }

        long interval = Math.max(10_000L, (endDate.getTime() - startDate.getTime()) * maxCount / count);
        int maxQueries = 3;
        ArrayList<LambdaRunInfo> runs = new ArrayList<>(maxCount);

        if (newestFirst) {
            long endTime = endDate.getTime();
            for (int i = 0; i < maxQueries; i++) {
                long startTime = Math.max(startDate.getTime(), endTime - interval);
                parts = LambdaRunInfo.partitions(startTime, endTime);
                var execs = mapper.selectLambdaRunsInfo(lambdaVersionId, lambdaAssignmentId, lane, trigger, errors,
                    new Timestamp(startTime), new Timestamp(endTime), parts);
                if (execs.isEmpty()) interval *= 3;
                else {
                    runs.addAll(execs);
                    if (runs.size() >= maxCount) break;
                }
                if (startTime == startDate.getTime()) break;
                endTime = startTime;
            }
        } else {
            long startTime = startDate.getTime();
            for (int i = 0; i < maxQueries; i++) {
                long endTime = Math.min(endDate.getTime(), startTime + interval);
                parts = LambdaRunInfo.partitions(startTime, endTime);
                var execs = mapper.selectLambdaRunsInfo(lambdaVersionId, lambdaAssignmentId, lane, trigger, errors,
                    new Timestamp(startTime), new Timestamp(endTime), parts);
                if (execs.isEmpty()) interval *= 3;
                else {
                    runs.addAll(execs);
                    if (runs.size() >= maxCount) break;
                }
                if (endTime == endDate.getTime()) break;
                startTime = endTime;
            }
        }

        if (runs.size() > 1) {
            runs.sort(Comparator.comparingLong(r -> sortDirection * r.requestDate.getTime()));
        }
        lambdaStatistics.runsInfo = runs.size() <= maxCount ? runs : runs.subList(0, maxCount);
        return lambdaStatistics;
    }

    @Override
    public void loadStatistics(Lambda lambda) {
        lambda.lambdaVersions = mapper.selectLambdaVersions(lambda.lambdaId, LambdaVersionStatus.ALL_ACTIVE, null);
        List<Integer> lambdaVersionIds = CollectionOps.map(lambda.lambdaVersions, v -> v.lambdaVersionId);
        if (lambdaVersionIds == null) return;
        Map<Integer, LambdaStatistics> statisticsMap = mapper.selectLambdaRunStatsDay(lambdaVersionIds);
        lambda.lambdaVersions.forEach(v -> v.statistics = statisticsMap.get(v.lambdaVersionId));
    }

    @Override
    public Timestamp getMaxStatDate() {
        return mapper.selectMaxDateInExecStatDay();
    }

    @Override
    public void aggregateLambdaStatistic(Datetime startDate, Datetime endDate) {
        mapper.insertLambdaExecStatDayAsSelect(LambdaRunInfo.partitionIndex(startDate.getTime()), startDate, endDate);
    }

    @Override
    public List<LambdaDailyMetrics> getRunsDay(Datetime startDate, Datetime endDate) {
        return mapper.selectRunsDay(LambdaRunInfo.partitionIndex(startDate.getTime()), startDate, endDate);
    }

    @Override
    public void insertDailyMetrics(List<LambdaDailyMetrics> metrics) {
        metrics.forEach(mapper::insertDailyMetrics);
    }

    @Override
    public List<LambdaDailyMetrics> getDailyMetrics(int lambdaId, Datetime date) {
        return mapper.selectDailyMetrics(lambdaId, date);
    }
}
