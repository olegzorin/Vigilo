package dev.olegz.vf.aws.local;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;

/**
 * Local file-system implementation of {@link CloudWatchLogsClient}. A log group is a directory
 * under {@code <root>/logs}, a log stream is an append file inside it, and the retention policy is
 * a {@code .retention} marker file. Group and stream names (which may contain '/') are
 * percent-encoded into single path segments. Only the operations used by
 * {@code dev.olegz.vf.aws.cloudwatch.CloudWatchLogsSupport} are implemented; the
 * non-paginated {@link #describeLogGroups} backs the paginator used by the support class.
 */
public final class LocalCloudWatchLogsClient implements CloudWatchLogsClient {

    private static final String RETENTION_FILE = ".retention";
    private static final String TIMESTAMP_SUFFIX = ".ts";

    // Export tasks are ephemeral; an in-memory record is sufficient for local runs.
    private final ConcurrentHashMap<String, ExportTask> exportTasks = new ConcurrentHashMap<>();

    /* ---------- log groups ---------- */

    @Override
    public CreateLogGroupResponse createLogGroup(CreateLogGroupRequest request) {
        File dir = groupDir(request.logGroupName());
        if (dir.isDirectory()) {
            throw ResourceAlreadyExistsException.builder().message("Log group already exists").build();
        }
        if (!dir.mkdirs()) {
            throw new UncheckedIOException(new IOException("Cannot create log group dir " + dir));
        }
        return CreateLogGroupResponse.builder().build();
    }

    @Override
    public PutRetentionPolicyResponse putRetentionPolicy(PutRetentionPolicyRequest request) {
        File dir = requireGroup(request.logGroupName());
        write(new File(dir, RETENTION_FILE).toPath(), Integer.toString(request.retentionInDays()));
        return PutRetentionPolicyResponse.builder().build();
    }

    @Override
    public DescribeLogGroupsResponse describeLogGroups(DescribeLogGroupsRequest request) {
        String prefix = request.logGroupNamePrefix();
        File[] dirs = LocalAws.logsRoot().listFiles(File::isDirectory);
        List<LogGroup> groups = new ArrayList<>();
        if (dirs != null) {
            for (File dir : dirs) {
                String name = decode(dir.getName());
                if ((prefix == null) || name.startsWith(prefix)) {
                    groups.add(toLogGroup(name, dir));
                }
            }
        }
        return DescribeLogGroupsResponse.builder().logGroups(groups).build();
    }

    private static LogGroup toLogGroup(String name, File dir) {
        LogGroup.Builder builder = LogGroup.builder().logGroupName(name);
        String retention = read(new File(dir, RETENTION_FILE).toPath());
        if (retention != null) builder.retentionInDays(Integer.parseInt(retention.trim()));
        return builder.build();
    }

    @Override
    public DeleteLogGroupResponse deleteLogGroup(DeleteLogGroupRequest request) {
        File dir = requireGroup(request.logGroupName());
        deleteRecursively(dir);
        return DeleteLogGroupResponse.builder().build();
    }

    /* ---------- log streams ---------- */

    @Override
    public CreateLogStreamResponse createLogStream(CreateLogStreamRequest request) {
        File dir = groupDir(request.logGroupName());
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new UncheckedIOException(new IOException("Cannot create log group dir " + dir));
        }
        File stream = new File(dir, encode(request.logStreamName()));
        if (stream.isFile()) {
            throw ResourceAlreadyExistsException.builder().message("Log stream already exists").build();
        }
        write(stream.toPath(), "");
        return CreateLogStreamResponse.builder().build();
    }

    @Override
    public PutLogEventsResponse putLogEvents(PutLogEventsRequest request) {
        File dir = groupDir(request.logGroupName());
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new UncheckedIOException(new IOException("Cannot create log group dir " + dir));
        }
        File stream = new File(dir, encode(request.logStreamName()));

        StringBuilder sb = new StringBuilder();
        long lastTimestamp = 0L;
        for (InputLogEvent event : request.logEvents()) {
            long ts = event.timestamp() == null ? 0L : event.timestamp();
            sb.append(ts).append(' ').append(event.message()).append('\n');
            if (ts > lastTimestamp) lastTimestamp = ts;
        }
        append(stream.toPath(), sb.toString());
        write(new File(dir, encode(request.logStreamName()) + TIMESTAMP_SUFFIX).toPath(), Long.toString(lastTimestamp));

        return PutLogEventsResponse.builder().build();
    }

    @Override
    public DescribeLogStreamsResponse describeLogStreams(DescribeLogStreamsRequest request) {
        File dir = groupDir(request.logGroupName());
        List<LogStream> streams = new ArrayList<>();
        File[] files = dir.listFiles(f -> f.isFile()
            && !f.getName().equals(RETENTION_FILE) && !f.getName().endsWith(TIMESTAMP_SUFFIX));
        if (files != null) {
            String prefix = request.logStreamNamePrefix();
            for (File file : files) {
                String name = decode(file.getName());
                if ((prefix == null) || name.startsWith(prefix)) {
                    streams.add(toLogStream(name, dir, file));
                }
            }
            streams.sort(Comparator.comparing(LogStream::logStreamName));
            Integer limit = request.limit();
            if ((limit != null) && (streams.size() > limit)) {
                streams = new ArrayList<>(streams.subList(0, limit));
            }
        }
        return DescribeLogStreamsResponse.builder().logStreams(streams).build();
    }

    private static LogStream toLogStream(String name, File dir, File file) {
        String ts = read(new File(dir, file.getName() + TIMESTAMP_SUFFIX).toPath());
        long lastEvent = ts != null ? Long.parseLong(ts.trim()) : file.lastModified();
        return LogStream.builder().logStreamName(name).lastEventTimestamp(lastEvent).build();
    }

    /* ---------- export tasks ---------- */

    @Override
    public CreateExportTaskResponse createExportTask(CreateExportTaskRequest request) {
        File dir = requireGroup(request.logGroupName());

        // Export every matching stream into the (local) destination S3 bucket.
        File[] files = dir.listFiles(f -> f.isFile()
            && !f.getName().equals(RETENTION_FILE) && !f.getName().endsWith(TIMESTAMP_SUFFIX));
        if (files != null) {
            String streamPrefix = request.logStreamNamePrefix();
            for (File file : files) {
                String name = decode(file.getName());
                if ((streamPrefix == null) || name.startsWith(streamPrefix)) {
                    File target = LocalAws.s3File(request.destination(), request.destinationPrefix() + '/' + name);
                    copy(file, target);
                }
            }
        }

        String taskId = "export-" + System.currentTimeMillis() + '-' + exportTasks.size();
        exportTasks.put(taskId, ExportTask.builder()
            .taskId(taskId)
            .taskName(request.taskName())
            .logGroupName(request.logGroupName())
            .destination(request.destination())
            .destinationPrefix(request.destinationPrefix())
            .from(request.from())
            .to(request.to())
            .status(s -> s.code(ExportTaskStatusCode.COMPLETED).message("Completed"))
            .build());
        return CreateExportTaskResponse.builder().taskId(taskId).build();
    }

    @Override
    public DescribeExportTasksResponse describeExportTasks(DescribeExportTasksRequest request) {
        ExportTask task = request.taskId() == null ? null : exportTasks.get(request.taskId());
        List<ExportTask> tasks = task == null ? List.of() : List.of(task);
        return DescribeExportTasksResponse.builder().exportTasks(tasks).build();
    }

    /* ---------- helpers ---------- */

    private static File groupDir(String logGroupName) {
        return new File(LocalAws.logsRoot(), encode(logGroupName));
    }

    private static File requireGroup(String logGroupName) {
        File dir = groupDir(logGroupName);
        if (!dir.isDirectory()) {
            throw ResourceNotFoundException.builder().message("Log group does not exist: " + logGroupName).build();
        }
        return dir;
    }

    /**
     * Percent-encode '/' and '%' so any name becomes a single safe path segment.
     */
    private static String encode(String name) {
        return name.replace("%", "%25").replace("/", "%2F");
    }

    private static String decode(String name) {
        return name.replace("%2F", "/").replace("%25", "%");
    }

    private static void write(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + path, e);
        }
    }

    private static void append(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot append " + path, e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }

    private static void copy(File source, File target) {
        File parent = target.getParentFile();
        if ((parent != null) && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new UncheckedIOException(new IOException("Cannot create directory " + parent));
        }
        try {
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot copy " + source + " to " + target, e);
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    @Override
    public String serviceName() {
        return CloudWatchLogsClient.SERVICE_NAME;
    }

    @Override
    public void close() {
    }
}
