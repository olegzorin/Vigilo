package dev.olegz.vf.worker.scheduler.job;

import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.util.DateFormatUtils;
import dev.olegz.vf.core.dao.LambdaStatisticsDao;
import dev.olegz.vf.core.dao.SystemDao;
import dev.olegz.vf.core.domain.lambdarun.LambdaDailyMetrics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RotateLambdaRunResultsPartitionsTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-06T09:00:00Z"), ZoneOffset.UTC);

    @Test
    void rotatesTomorrowAndAggregatesEveryMissingCompletedDay() {
        List<String> operations = new ArrayList<>();
        LambdaDailyMetrics metrics = new LambdaDailyMetrics();
        SystemDao systemDao = proxy(SystemDao.class, (proxy, method, args) -> {
            if (method.getName().equals("truncateTablePartition")) {
                operations.add("truncate:" + args[0] + ':' + args[1]);
            }
            return defaultValue(method.getReturnType());
        });
        LambdaStatisticsDao statisticsDao = proxy(LambdaStatisticsDao.class, (proxy, method, args) -> switch (method.getName()) {
            case "getMaxStatDate" -> Timestamp.from(Instant.parse("2026-08-03T00:00:00Z"));
            case "aggregateLambdaStatistic" -> {
                operations.add("aggregate:" + epochDay(args[0]));
                yield null;
            }
            case "getRunsDay" -> {
                operations.add("metrics:" + epochDay(args[0]));
                yield List.of(metrics);
            }
            case "insertDailyMetrics" -> {
                operations.add("insertMetrics:" + ((List<?>) args[0]).size());
                yield null;
            }
            default -> defaultValue(method.getReturnType());
        });
        RotateLambdaRunResultsPartitions job = new RotateLambdaRunResultsPartitions(
            systemDao, statisticsDao, CLOCK, _ -> 5);

        job.run();

        assertEquals(List.of(
            "truncate:lambda_run_results:p5",
            "aggregate:2026-08-04", "metrics:2026-08-04", "insertMetrics:1",
            "aggregate:2026-08-05", "metrics:2026-08-05", "insertMetrics:1"
        ), operations);
        assertEquals("RotateLambdaRunResultsPartitions", job.name());
        assertEquals("0 0 9 * * ?", job.cron());
        assertEquals(DateFormatUtils.DEFAULT_TIMEZONE_ID, job.timeZoneId());
    }

    @Test
    void startsWithYesterdayWhenNoDailyStatisticsExist() {
        List<String> aggregatedDays = new ArrayList<>();
        SystemDao systemDao = proxy(SystemDao.class, (proxy, method, args) -> defaultValue(method.getReturnType()));
        LambdaStatisticsDao statisticsDao = proxy(LambdaStatisticsDao.class, (proxy, method, args) -> {
            if (method.getName().equals("aggregateLambdaStatistic")) aggregatedDays.add(epochDay(args[0]));
            return method.getName().equals("getRunsDay") ? List.of() : defaultValue(method.getReturnType());
        });

        new RotateLambdaRunResultsPartitions(systemDao, statisticsDao, CLOCK, _ -> 0).run();

        assertEquals(List.of("2026-08-05"), aggregatedDays);
    }

    @Test
    void aggregationStillRunsWhenPartitionTruncationFails() {
        List<String> operations = new ArrayList<>();
        SystemDao systemDao = proxy(SystemDao.class, (proxy, method, args) -> {
            if (method.getName().equals("truncateTablePartition")) throw new IllegalStateException("truncate failed");
            return defaultValue(method.getReturnType());
        });
        LambdaStatisticsDao statisticsDao = proxy(LambdaStatisticsDao.class, (proxy, method, args) -> {
            if (method.getName().equals("aggregateLambdaStatistic")) operations.add("aggregate:" + epochDay(args[0]));
            return method.getName().equals("getRunsDay") ? List.of() : defaultValue(method.getReturnType());
        });

        new RotateLambdaRunResultsPartitions(systemDao, statisticsDao, CLOCK, _ -> 0).run();

        assertEquals(List.of("aggregate:2026-08-05"), operations);
    }

    private static String epochDay(Object value) {
        return Instant.ofEpochMilli(((Datetime) value).getTime()).atZone(ZoneOffset.UTC).toLocalDate().toString();
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
