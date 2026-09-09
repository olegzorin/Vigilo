package dev.olegz.vf.core.dao.metric;

/**
 * Snapshot of a lambda-workload operation. Calls are execution attempts, so retries and failed calls
 * both contribute to call rate and duration; failures are also reported separately. Rates cover
 * completed one-minute slices and duration values are microseconds.
 */
public record LambdaWorkloadMetricSnapshot(
    String name, String elapsed,
    double avgCPM_1Hour, int maxCPM_1Hour, long avgCallTimeMicros_1Hour, int calls_1Hour, int failures_1Hour,
    double avgCPM_6Hours, int maxCPM_6Hours, long avgCallTimeMicros_6Hours, int calls_6Hours, int failures_6Hours,
    double avgCPM_3Days, int maxCPM_3Days, long avgCallTimeMicros_3Days, int calls_3Days, int failures_3Days)
{
}
