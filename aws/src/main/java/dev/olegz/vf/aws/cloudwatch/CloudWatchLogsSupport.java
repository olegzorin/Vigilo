package dev.olegz.vf.aws.cloudwatch;

import java.util.List;

import dev.olegz.vf.aws.client.AwsClients;
import dev.olegz.vf.aws.error.AwsExceptions;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;

/**
 * CloudWatch Logs: log group/stream lifecycle, event writing and export tasks.
 * <p>
 * Stateless static helper, matching the module's other thin AWS wrappers
 * ({@link dev.olegz.vf.aws.ecr.EcrSupport}, {@link dev.olegz.vf.aws.iam.IamSupport}).
 * The client comes from {@link AwsClients#cloudWatchLogsClient()}, so it is the real CloudWatch Logs
 * client or the local one ({@link dev.olegz.vf.aws.local.LocalCloudWatchLogsClient}) per
 * {@code vf.aws.local}.
 */
public final class CloudWatchLogsSupport {
    private CloudWatchLogsSupport() {
    }

    private static final Logger logger = LoggerFactory.getLogger(CloudWatchLogsSupport.class);

    private static final int[] VALID_RETENTION_PERIODS = {
        1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1096, 1827, 2192, 2557, 2922, 3288, 3653
    };

    /**
     * The shared {@link CloudWatchLogsClient}, built lazily on first use and held for the JVM
     * lifetime; rebuilt if {@link #shutdown()} closed it (e.g. between test contexts).
     */
    private static final AwsClients.LazyClient<CloudWatchLogsClient> CLIENT =
        AwsClients.lazyClient("CloudWatch Logs", AwsClients::cloudWatchLogsClient);

    private static CloudWatchLogsClient client() {
        return CLIENT.get();
    }

    /** Idempotent: closes the shared client; safe to call more than once. */
    public static void shutdown() {
        CLIENT.shutdown();
    }

    private static int getValidRetentionPeriod(int retentionPeriod) {
        for (int val : VALID_RETENTION_PERIODS) {
            if (val >= retentionPeriod) return val;
        }
        return VALID_RETENTION_PERIODS[VALID_RETENTION_PERIODS.length - 1];
    }

    public static void createLogGroup(String logGroupName, int retentionDays) {
        CloudWatchLogsClient c = client();
        try {
            c.createLogGroup(
                r -> r.logGroupName(logGroupName).logGroupClass(LogGroupClass.STANDARD)
            );
        } catch (ResourceAlreadyExistsException ignore) {
        } catch (Exception e) {
            throw AwsExceptions.wrapAwsException(e, "Exception while creating log group " + logGroupName);
        }
        try {
            c.putRetentionPolicy(
                r -> r.logGroupName(logGroupName).retentionInDays(getValidRetentionPeriod(retentionDays))
            );
        } catch (Exception e) {
            AwsExceptions.logAwsExceptionAsError(logger, e, "Exception while setting retention policy forlog group " + logGroupName);
        }
    }

    public static int getLogGroupRetentionDays(String logGroupName) {
        LogGroup logGroup = findLogGroup(logGroupName);
        return (logGroup == null) || (logGroup.retentionInDays() == null) ? 0 : logGroup.retentionInDays();
    }

    public static LogGroup findLogGroup(String logGroupName) {
        CloudWatchLogsClient c = client();
        try {
            var pages = c.describeLogGroupsPaginator(r -> r.logGroupNamePrefix(logGroupName));
            for (var page : pages) {
                for (LogGroup logGroup : page.logGroups()) {
                    if (logGroupName.equals(logGroup.logGroupName())) return logGroup;
                }
            }
        } catch (Exception e) {
            AwsExceptions.logAwsExceptionAsError(logger, e, "Exception while looking for log group " + logGroupName);
        }
        return null;
    }

    public static void deleteLogGroup(String logGroupName) {
        CloudWatchLogsClient c = client();
        try {
            c.deleteLogGroup(r -> r.logGroupName(logGroupName));
        } catch (ResourceNotFoundException ignore) {
        } catch (Exception e) {
            AwsExceptions.logAwsExceptionAsError(logger, e, "Exception while deleting log group " + logGroupName);
        }
    }

    public static void createLogStream(String logGroupName, String logStreamName, String initLogMessage) {
        CloudWatchLogsClient c = client();
        try {
            c.createLogStream(r -> r.logGroupName(logGroupName).logStreamName(logStreamName));

            if (initLogMessage != null) {
                InputLogEvent logEvent = InputLogEvent.builder().timestamp(System.currentTimeMillis()).message(initLogMessage).build();
                c.putLogEvents(r -> r.logGroupName(logGroupName).logStreamName(logStreamName).logEvents(logEvent));
            }
        } catch (ResourceAlreadyExistsException ignore) {
        } catch (Exception e) {
            throw AwsExceptions.wrapAwsException(e, "Exception while creating log stream, logGroup=" + logGroupName + ", logStream=" + logStreamName);
        }
    }

    public static Long getLastEventTimestamp(String logGroupName, String logStreamName) {
        CloudWatchLogsClient c = client();
        try {
            List<LogStream> logStreams = c.describeLogStreams(
                r -> r.logGroupName(logGroupName).logStreamNamePrefix(logStreamName).limit(1)
            ).logStreams();

            if ((logStreams == null) || logStreams.isEmpty()) return null;

            if (logStreams.size() > 1) {
                logger.warn("There are " + logStreams.size() + " log streams for logGroupName=" + logGroupName + ", logStreamName=" + logStreamName);
            } else {
                return logStreams.getFirst().lastEventTimestamp();
            }
        } catch (ResourceNotFoundException ignore) {
        } catch (Exception e) {
            AwsExceptions.logAwsExceptionAsError(logger, e, "Exception while getting last event timestamp, logGroup=" + logGroupName + ", logStream=" + logStreamName);
        }
        return null;
    }

    public static void writeLogEvents(String logGroupName, String logStreamName, List<InputLogEvent> logEvents, List<String> warnings) {
        var request = PutLogEventsRequest.builder()
            .logGroupName(logGroupName)
            .logStreamName(logStreamName)
            .logEvents(logEvents).build();

        CloudWatchLogsClient c = client();
        try {
            var rejectInfo = c.putLogEvents(request).rejectedLogEventsInfo();
            if (rejectInfo != null) {
                Integer tooNewStartIndex = rejectInfo.tooNewLogEventStartIndex();
                if (tooNewStartIndex != null) warnings.add("tooNewStartIndex=" + tooNewStartIndex);

                Integer expiredEndIndex = rejectInfo.expiredLogEventEndIndex();
                if (expiredEndIndex != null) warnings.add("expiredEndIndex=" + expiredEndIndex);

                Integer tooOldEndIndex = rejectInfo.tooOldLogEventEndIndex();
                if (tooOldEndIndex != null) warnings.add("tooOldEndIndex=" + tooOldEndIndex);
            }
        } catch (ResourceNotFoundException ignore) {
            logger.warn("Could not write log events, log group does not exist, logGroupName=" + logGroupName);
        } catch (Exception e) {
            AwsExceptions.logAwsExceptionAsError(logger, e, "Exception while writing log events, logGroupName=" + logGroupName);
        }
    }

    public static String createExportTask(
        String taskName, String logGroupName, String logStreamName, long startDate, long endDate,
        String destinationBucket, String destinationPrefix) {
        var request = CreateExportTaskRequest.builder()
            .taskName(taskName)
            .logGroupName(logGroupName)
            .logStreamNamePrefix(logStreamName)
            .from(startDate)
            .to(endDate)
            .destination(destinationBucket)
            .destinationPrefix(destinationPrefix)
            .build();
        CloudWatchLogsClient c = client();
        try {
            return c.createExportTask(request).taskId();
        } catch (ResourceNotFoundException e) {
            throw new ObjectNotFoundException("Cannot create export task: " + e.getMessage());
        } catch (Exception e) {
            throw AwsExceptions.wrapAwsException(e, "Exception while creating export task");
        }
    }

    public static ExportTask getExportTask(String taskId) {
        CloudWatchLogsClient c = client();
        List<ExportTask> tasks = c.describeExportTasks(r -> r.taskId(taskId).limit(1)).exportTasks();
        if ((tasks == null) || tasks.isEmpty()) {
            throw new ObjectNotFoundException("Not found export task");
        }
        return tasks.getFirst();
    }

}
