package dev.olegz.vf.core.domain.lambdarun;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.util.CollectionOps;
import dev.olegz.vf.common.util.DateFormatUtils;
import dev.olegz.vf.core.messaging.MessageDispatcher;
import dev.olegz.vf.messaging.Messaging;
import dev.olegz.vf.messaging.MessagingProvider;

public class LambdaAssignmentLog implements Comparable<LambdaAssignmentLog> {
    public long requestId;
    public int lambdaAssignmentId;
    @JsonProperty("flow")
    public InvocationLane lane = InvocationLane.DEFAULT;
    public int lambdaId;
    public int batchIndex;
    public List<LambdaLogEvent> logEvents;

    @Override
    public int compareTo(LambdaAssignmentLog other) {
        return this.requestId > other.requestId ? 1 :
            this.requestId < other.requestId ? -1 :
                Integer.compare(this.batchIndex, other.batchIndex);
    }

    @Override
    public boolean equals(Object o) {
        return (this == o) ||
            (o instanceof LambdaAssignmentLog other) && this.isDuplicateOf(other);
    }

    @Override
    public int hashCode() {
        throw new ApplicationFailureException("hashCode not designed for " + this.getClass().getName());
    }

    @Override
    public String toString() {
        return "{requestId=" + requestId + ", lambdaAssignmentId=" + lambdaAssignmentId + ", lambdaId=" + lambdaId +
            ", batchIndex=" + batchIndex + (lane != InvocationLane.DEFAULT ? ", lane=" + lane : "") +
            ((logEvents == null) || logEvents.isEmpty() ? "" :
                ", size=" + logEvents.size() +
                    ", from=" + DateFormatUtils.logTimestamp(logEvents.getFirst().timestamp) +
                    ", to=" + DateFormatUtils.logTimestamp(logEvents.getLast().timestamp)) +
            '}';
    }

    public boolean isDuplicateOf(LambdaAssignmentLog other) {
        return this.requestId == other.requestId && this.batchIndex == other.batchIndex;
    }

    public static List<LambdaLogEvent> prepare(List<LambdaLogEvent> logEvents, long startTime, long endTime) {
        int total = logEvents == null ? 0 : logEvents.size();

        List<LambdaLogEvent> entries = new ArrayList<>(total + 3);
        // Add a header first as it is more efficient than moving all array records to one position back
        entries.add(new LambdaLogEvent("BEGIN LOG", startTime));

        int emptyCount = 0;
        int outsideDateRangeCount = 0;
        if (total > 0) {
            boolean sorted = true;
            long lastTime = startTime;
            for (var logEvent : logEvents) {
                if ((logEvent == null) || (logEvent.text == null) || (logEvent.text.length == 0)) {
                    emptyCount++;
                    continue;
                }
                if ((logEvent.timestamp < startTime) || (logEvent.timestamp > endTime)) {
                    outsideDateRangeCount++;
                    continue;
                }
                if (sorted && (logEvent.timestamp < lastTime)) {
                    sorted = false;
                }
                lastTime = logEvent.timestamp;
                entries.add(logEvent);
            }

            // If not sorted, then the number of real entries is at least 2
            if (!sorted) {
                // exclude header from sorting
                entries.subList(1, entries.size()).sort(Comparator.comparingLong(e -> e.timestamp));
            }
        }

        entries.add(
            new LambdaLogEvent("LOG REPORT: N of log events: " + total +
                (emptyCount > 0 ? "; Omitted " + emptyCount + " empty events" : "") +
                (outsideDateRangeCount > 0 ? "; Excluded " + outsideDateRangeCount + " events outside date range" : ""),
                endTime)
        );
        entries.add(new LambdaLogEvent("END LOG", endTime));

        return entries;
    }

    public static void publish(long requestId, int lambdaAssignmentId, InvocationLane lane, int lambdaId, List<LambdaLogEvent> entries) {
        LambdaAssignmentLog lambdaAssignmentLog = new LambdaAssignmentLog();
        lambdaAssignmentLog.requestId = requestId;
        lambdaAssignmentLog.lambdaAssignmentId = lambdaAssignmentId;
        lambdaAssignmentLog.lane = lane;
        lambdaAssignmentLog.lambdaId = lambdaId;

        int logSize = LOG_FIELDS_SIZE;
        int from = 0;
        int size = entries.size();
        // Set non-zero seqno only for log events provided by lambda (exclude footers)
        int seqnoMax = size - 2;

        for (int to = 0; to < size; to++) {
            var logEvent = entries.get(to);
            if (to < seqnoMax) logEvent.seqno = to;

            int eventSize = logEventSerializedSize(logEvent);
            logSize += eventSize;
            if (logSize > Messaging.broker(MessagingProvider.KAFKA).maxMessageSize()) {
                // publish all except the current one
                // expecting that each event size is less than the max value and from < to
                lambdaAssignmentLog.logEvents = entries.subList(from, to);
                MessageDispatcher.sendLambdaAssignmentLog(lambdaAssignmentLog);
                lambdaAssignmentLog.batchIndex++;
                logSize = LOG_FIELDS_SIZE + eventSize;
                from = to;
            }
        }

        lambdaAssignmentLog.logEvents = from == 0 ? entries : entries.subList(from, size);
        MessageDispatcher.sendLambdaAssignmentLog(lambdaAssignmentLog);
    }

    private static final int LOG_FIELDS_SIZE = 73;

    // Empirical upper estimate for the value of BytesMapper.writeValue(lambdaAssignmentLog).length
    // The actual value is within the interval [0.99 * estimate, estimate]
    int serializedSize() {
        return LOG_FIELDS_SIZE + CollectionOps.sumIntValues(logEvents, LambdaAssignmentLog::logEventSerializedSize);
    }

    private static int logEventSerializedSize(LambdaLogEvent e) {
        return 45 + e.text.length + (e.text.length - 1) / 7;
    }
}
