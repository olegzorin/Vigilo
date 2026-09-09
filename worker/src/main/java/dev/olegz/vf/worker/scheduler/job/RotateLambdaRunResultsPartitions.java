package dev.olegz.vf.worker.scheduler.job;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.LongToIntFunction;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.util.DateFormatUtils;
import dev.olegz.vf.core.dao.LambdaStatisticsDao;
import dev.olegz.vf.core.dao.SystemDao;
import dev.olegz.vf.core.domain.lambdarun.LambdaDailyMetrics;
import dev.olegz.vf.core.domain.lambdarun.LambdaRunInfo;
import dev.olegz.vf.worker.scheduler.CronJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RotateLambdaRunResultsPartitions implements CronJob {
    private static final Logger logger = LoggerFactory.getLogger(RotateLambdaRunResultsPartitions.class);
    private static final String TABLE_NAME = "lambda_run_results";

    private final SystemDao systemDao;
    private final LambdaStatisticsDao lambdaStatisticsDao;
    private final Clock clock;
    private final LongToIntFunction partitionIndex;

    @Autowired
    public RotateLambdaRunResultsPartitions(SystemDao systemDao, LambdaStatisticsDao lambdaStatisticsDao) {
        this(systemDao, lambdaStatisticsDao, Clock.systemUTC(), LambdaRunInfo::partitionIndex);
    }

    RotateLambdaRunResultsPartitions(SystemDao systemDao, LambdaStatisticsDao lambdaStatisticsDao, Clock clock,
        LongToIntFunction partitionIndex)
    {
        this.systemDao = systemDao;
        this.lambdaStatisticsDao = lambdaStatisticsDao;
        this.clock = clock;
        this.partitionIndex = partitionIndex;
    }

    @Override
    public String name() {
        return "RotateLambdaRunResultsPartitions";
    }

    @Override
    public String cron() {
        return "0 0 9 * * ?";
    }

    @Override
    public String timeZoneId() {
        return DateFormatUtils.DEFAULT_TIMEZONE_ID;
    }

    @Override
    public void run() {
        LocalDate today = LocalDate.now(clock);
        try {
            truncateTomorrowPartition(today);
        } catch (Exception e) {
            logger.error("Exception truncating tomorrow's {} partition", TABLE_NAME, e);
        }

        try {
            aggregateCompletedDays(today);
        } catch (Exception e) {
            logger.error("Exception aggregating completed lambda-run-result days", e);
        }
    }

    private void truncateTomorrowPartition(LocalDate today) {
        long tomorrow = startOfDay(today.plusDays(1));
        String partitionName = "p" + partitionIndex.applyAsInt(tomorrow);
        systemDao.truncateTablePartition(TABLE_NAME, partitionName);
        logger.info("Truncated {} partition {}", TABLE_NAME, partitionName);
    }

    private void aggregateCompletedDays(LocalDate today) {
        LocalDate nextDate = firstDateToAggregate(today, lambdaStatisticsDao.getMaxStatDate());
        while (nextDate.isBefore(today)) {
            LocalDate endDate = nextDate.plusDays(1);
            Datetime start = new Datetime(startOfDay(nextDate));
            Datetime end = new Datetime(startOfDay(endDate));

            lambdaStatisticsDao.aggregateLambdaStatistic(start, end);
            List<LambdaDailyMetrics> metrics = lambdaStatisticsDao.getRunsDay(start, end);
            if ((metrics != null) && !metrics.isEmpty()) {
                lambdaStatisticsDao.insertDailyMetrics(metrics);
            }
            logger.info("Aggregated lambda run results for {}", nextDate);
            nextDate = endDate;
        }
    }

    private static LocalDate firstDateToAggregate(LocalDate today, Timestamp maxStatDate) {
        if (maxStatDate == null) return today.minusDays(1);
        return maxStatDate.toInstant().atZone(ZoneOffset.UTC).toLocalDate().plusDays(1);
    }

    private static long startOfDay(LocalDate date) {
        Instant instant = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        return instant.toEpochMilli();
    }
}
