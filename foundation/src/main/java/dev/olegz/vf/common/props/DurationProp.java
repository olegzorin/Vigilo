package dev.olegz.vf.common.props;

import java.time.Duration;

/**
 * Registry of duration properties that are read on hot paths.
 * <p>
 * Configured values consist of a non-negative integer followed by one of the supported units:
 * {@code ms} (milliseconds), {@code s} (seconds), {@code m} (minutes), {@code h} (hours), or
 * {@code d} (days).
 */
public enum DurationProp {

    // api
    USER_KEY_EXPIRY("vf.account.user.key.expiry", Duration.ofDays(30)),

    // aws
    AWS_SQS_LAMBDA_RESULTS_RETENTION("vf.aws.sqs.lambdaResults.retention", Duration.ofDays(4)),
    AWS_SQS_LAMBDA_RESULTS_DEAD_LETTER_RETENTION("vf.aws.sqs.lambdaResults.deadLetterRetention", Duration.ofDays(14)),

    // scheduler
    SCHEDULER_JOB_LOCK_LEASE("vf.scheduler.jobLockLease", Duration.ofMinutes(5)),

    // lambdas
    LAMBDA_TIMEOUT("vf.lambda.timeout", Duration.ofSeconds(600)),
    LAMBDA_START_TIMEOUT("vf.lambda.timeout.start", Duration.ofSeconds(10)),
    LAMBDA_ASYNC_TIMEOUT("vf.lambda.async.timeout", Duration.ofSeconds(900)),
    LAMBDA_ASYNC_START_TIMEOUT("vf.lambda.sqs.timeout", Duration.ofSeconds(200)),
    LAMBDA_ASYNC_KEY_EXPIRY("vf.lambda.async.key.exp", Duration.ofSeconds(3600)),
    LAMBDA_USER_KEY_EXPIRY("vf.lambda.user.key.exp", Duration.ofSeconds(600)),
    LAMBDA_RETRIES_DELAY("vf.lambda.retries.delay", Duration.ofSeconds(3)),
    LAMBDA_RETRY_OUTBOX_CLAIM_LEASE("vf.lambda.retries.outbox.claimLease", Duration.ofMinutes(2)),
    LAMBDA_ASYNC_SUBMISSION_RETRY_DELAY("vf.lambda.async.submission.retryDelay", Duration.ofSeconds(3)),
    LAMBDA_ASYNC_SUBMISSION_RETRY_MAX_DELAY("vf.lambda.async.submission.retryMaxDelay", Duration.ofMinutes(1)),
    LAMBDA_ASYNC_SUBMISSION_CLAIM_LEASE("vf.lambda.async.submission.claimLease", Duration.ofMinutes(2)),
    LAMBDA_COMPLETION_OUTBOX_CLAIM_LEASE("vf.lambda.completions.outbox.claimLease", Duration.ofMinutes(2)),
    LAMBDA_RESET_OUTBOX_CLAIM_LEASE("vf.lambda.resets.outbox.claimLease", Duration.ofMinutes(2)),
    LAMBDA_EVENT_RECEIPT_RETENTION("vf.lambda.eventReceipt.retention", Duration.ofDays(1)),
    CACHE_INVALIDATION_OUTBOX_CLAIM_LEASE("vf.cache.invalidation.outbox.claimLease", Duration.ofMinutes(2)),
    LAMBDA_EXECUTION_EXPIRY_GRACE_PERIOD("vf.lambda.expiry.gracePeriod", Duration.ofSeconds(30)),
    LAMBDA_ASSIGNMENT_DATA_CLEANUP_DELAY("vf.lambda.assignment.dataCleanupDelay", Duration.ofDays(1)),
    LAMBDA_SCHEDULE_TRIGGERING_INTERVAL("vf.lambda.schedule.triggeringInterval", Duration.ofSeconds(1)),
    LAMBDA_ERROR_SILENCE_INTERVAL("vf.lambda.errorSilenceInterval", Duration.ofHours(24)),
    LAMBDA_DEPLOYMENT_TIMEOUT("vf.lambda.build.deploymentTimeout", Duration.ofSeconds(900)),
    LAMBDA_STATS_MAX_DELAY("vf.lambda.statTimeout", Duration.ofSeconds(15)),
    DEV_LAMBDA_ASSIGNMENT_TIME_TO_RUN("vf.lambda.dev.assignment.timeToRun", Duration.ofSeconds(3600)),
    LAMBDA_LOG_RETENTION("vf.lambda.assignmentLog.retentionDays", Duration.ofDays(60)),
    ;

    final String label;
    final Duration defaultValue;

    DurationProp(String label, Duration defaultValue) {
        this.label = label;
        this.defaultValue = defaultValue;
    }
}
