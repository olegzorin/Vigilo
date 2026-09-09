package dev.olegz.vf.core.domain.lambdarun;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import dev.olegz.vf.core.domain.KeyValuePair;
import org.springframework.util.Assert;

/**
 Lambda function metrics.
 See https://docs.aws.amazon.com/lambda/latest/dg/monitoring-metrics.html
 */
public class LambdaDailyMetrics {
    public int lambdaVersionId;
    public Timestamp startDate;
    public int lambdaId;
    public InvocationLane lane;
    public String functionNames;
    public double memory;

    // The value of Invocations equals the number of requests billed.
    public int invocations;
    // Exceptions that code throws and exceptions that the Lambda runtime throws, such as timeouts and configuration errors.
    public int errors;
    // Throttled requests don't count as either Invocations or Errors.
    public int throttles;
    // The billed duration for an invocation is the value of Duration rounded up to the nearest millisecond.
    // Duration does not include cold start time.
    public long totalDuration;          // millis

    public int maxConcurrentExecutions;
    public int avgConcurrentExecutions;

    //For asynchronous invocation, the number of times that Lambda attempts to send an event to a dead-letter queue (DLQ) but fails.
    public int deadLetterErrors;
    // The number of events that Lambda successfully queues for processing. Mismatches between AsyncEventsReceived and Invocations
    // can indicate a disparity in processing, events being dropped, or a potential queue backlog.
    public int asyncEventsReceived;
    // The number of events that are dropped without successfully executing the function. If you configure a dead-letter queue (DLQ)
    // or OnFailure destination, then events are sent there before they're dropped.
    public int asyncEventsDropped;
    // The time between when Lambda successfully queues the event and when the function is invoked.
    public long asyncEventAge;       // millis
    public long maxAsyncEventAge;    // millis

    public long gbSBilled;

    @Override
    public String toString() {
        return "LambdaDailyMetrics{" +
            "\nlambdaVersionId=" + lambdaVersionId +
            "\nstartDate=" + startDate +
            "\nlambdaId=" + lambdaId +
            "\nlane=" + lane +
            "\ninvocations=" + invocations +
            "\nthrottles=" + throttles +
            "\nerrors=" + errors +
            "\ntotalDuration=" + totalDuration +
            "\nmaxConcurrentExecutions=" + maxConcurrentExecutions +
            "\navgConcurrentExecutions=" + avgConcurrentExecutions +
            (lane == InvocationLane.DEFAULT ? "" : "\nasyncEventsReceived=" + asyncEventsReceived +
                "\nasyncEventsDropped=" + asyncEventsDropped +
                "\ndeadLetterErrors=" + deadLetterErrors +
                "\nasyncEventAge=" + asyncEventAge +
                "\nmaxAsyncEventAge=" + maxAsyncEventAge) +
            "\ngbSBilled=" + gbSBilled +
            "\n}";
    }

    private List<KeyValuePair<String, String>> parsedFunctions;

    public List<KeyValuePair<String, String>> functions() {
        if (parsedFunctions == null) {
            Assert.notNull(functionNames, () -> "Missing functions, lambdaVersionId=" + lambdaVersionId + ", lane=" + lane + ", startDate=" + startDate);
            String[] names = functionNames.split(",", -1);
            parsedFunctions = new ArrayList<>(names.length);
            for (int i = 0; i < names.length; i++) {
                parsedFunctions.add(new KeyValuePair<>("_" + i + '_' + lane.code() + '_' + lambdaVersionId, names[i]));
            }
        }
        return parsedFunctions;
    }

}
