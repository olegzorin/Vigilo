package dev.olegz.vf.core.service.lambda;

import java.time.Duration;

import dev.olegz.vf.aws.cloudwatch.CloudWatchLogsSupport;
import dev.olegz.vf.common.props.DurationProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.dao.LambdaDao;
import dev.olegz.vf.core.dao.SystemDao;
import dev.olegz.vf.core.domain.lambdarun.LambdaAssignmentLog;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import static dev.olegz.vf.core.domain.lambdaversion.LambdaVersionStatus.PRODUCTION;
import static dev.olegz.vf.core.domain.lambdaversion.LambdaVersionStatus.TESTING;

@Service("lambdaLogService")
public class LambdaLogServiceImpl implements LambdaLogService {
    private static final Logger logger = LoggerFactory.getLogger(LambdaLogServiceImpl.class);

    private static final LambdaVersionStatus[] LOG_VERSION_STATUSES = {TESTING, PRODUCTION};

    private static String getS3LogsBucket() {
        return PropertyStore.getString("vf.aws.s3.logsBucket");
    }

    private final LambdaDao lambdaDao;
    private final SystemDao systemDao;
    public LambdaLogServiceImpl(LambdaDao lambdaDao, SystemDao systemDao) {
        this.lambdaDao = lambdaDao;
        this.systemDao = systemDao;
    }

    @Override
    public void createFunctionLogGroup(String functionName) {
        try {
            CloudWatchLogsSupport.createLogGroup(
                LambdaLogService.functionLogGroupNamePrefix + functionName,
                Math.toIntExact(PropertyStore.getDuration(DurationProp.LAMBDA_LOG_RETENTION).toDays())
            );
        } catch (Exception e) {
            logger.warn("Exception while creating CloudWatch log group for function " + functionName + '\n' + e);
        }
    }


    @Override
    public void deleteFunctionLogGroup(String functionName) {
        CloudWatchLogsSupport.deleteLogGroup(LambdaLogService.functionLogGroupNamePrefix + functionName);
    }

    /*
     * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/cloudwatchlogs/CloudWatchLogsClient.html#putLogEvents(software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsRequest)
     * The batch of events must satisfy the following constraints:
     *
     *   The maximum batch size is 1 MB (1,048,576 bytes). This size is calculated as the sum of all event
     *     messages in UTF-8, plus 26 bytes for each log event.
     *   Each log event size (message size in UTF-8 plus 26 bytes) can be no larger than 256 KB (262,144 bytes).
     *   The maximum number of log events in a batch is 10,000.
     *
     *   None of the log events in the batch can be more than 2 hours in the future or 14 days in the past.
     *     Also, none of the log events can be from earlier than the retention period of the log group.
     *   The log events in the batch must be in chronological order by their timestamp.
     *   A batch of log events in a single request cannot span more than 24 hours.
     */
    private static final int batchMaxDataSize = 1024 * 1024;
    private static final int batchMaxEventsCount = 1000;
    private static final int logEventMetadataSize = 26;

    private static final Duration ONE_DAY = Duration.ofDays(1);
    private static final Duration ONE_HOUR = Duration.ofHours(1);
    private static final long batchMaxTimeSpan = ONE_DAY.toMillis();

    private static boolean checkLogEventTimestamp(long timestamp) {
        long minTime = System.currentTimeMillis() - 14 * ONE_DAY.toMillis() + 60_000L; // plus 1 minute for API call delay
        long maxTime = System.currentTimeMillis() + 2 * ONE_HOUR.toMillis();
        return (timestamp >= minTime) && (timestamp <= maxTime);
    }

    @Override
    public void writeLambdaAssignmentLog(LambdaAssignmentLog log) {
        // TODO implement
    }

    /*************************
     *    CloudWatch Metric
     ************************/

    private static final int PERIOD = Math.toIntExact(ONE_DAY.toSeconds());
    private static final String NAMESPACE = "AWS/Lambda";
    private static final String DIMENSION = "FunctionName";

    private static final String[][] STAT_METRICS = {
        {"Sum", "Invocations"},
        {"Sum", "Throttles"},
        {"Sum", "Errors"},
        {"Sum", "Duration"},
        {"Average", "ConcurrentExecutions"},
        {"Maximum", "ConcurrentExecutions"},
        // metrics for async invocation
        {"Sum", "AsyncEventsReceived"},
        {"Sum", "AsyncEventsDropped"},
        {"Sum", "DeadLetterErrors"},
        {"Sum", "AsyncEventAge"},
        {"Maximum", "AsyncEventAge"}
    };


}
