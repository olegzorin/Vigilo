package dev.olegz.vf.worker.service;

import java.time.Duration;

import dev.olegz.vf.aws.sqs.SqsSupport;
import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.props.DurationProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;

/** Provisions the Lambda Destinations result queue and its dead-letter queue. */
public final class LambdaResultQueueProvisioner {
    private static final int MAX_SQS_RETENTION_SECONDS = 14 * 24 * 60 * 60;
    private static final int MAX_SQS_VISIBILITY_TIMEOUT_SECONDS = 12 * 60 * 60;
    private static final int DEFAULT_VISIBILITY_TIMEOUT_SECONDS = 60;
    private static final int DEFAULT_MAX_RECEIVE_COUNT = 10;

    private LambdaResultQueueProvisioner() {
    }

    public static String provision() {
        int deadLetterRetention = seconds(
            DurationProp.AWS_SQS_LAMBDA_RESULTS_DEAD_LETTER_RETENTION,
            "vf.aws.sqs.lambdaResults.deadLetterRetention", 60, MAX_SQS_RETENTION_SECONDS);
        int visibilityTimeout = PropertyStore.getInt(
            "vf.sqs.visibilityTimeoutSeconds", DEFAULT_VISIBILITY_TIMEOUT_SECONDS);
        if ((visibilityTimeout < 0) || (visibilityTimeout > MAX_SQS_VISIBILITY_TIMEOUT_SECONDS)) {
            throw new ApplicationFailureException("vf.sqs.visibilityTimeoutSeconds must be between 0 and "
                + MAX_SQS_VISIBILITY_TIMEOUT_SECONDS + ": " + visibilityTimeout);
        }
        String deadLetterQueueArn = SqsSupport.makeSqsQueue(
            Lambda.SQS_RESULT_DEAD_LETTER_QUEUE, 0, deadLetterRetention, visibilityTimeout);

        int retention = seconds(DurationProp.AWS_SQS_LAMBDA_RESULTS_RETENTION,
            "vf.aws.sqs.lambdaResults.retention", 60, MAX_SQS_RETENTION_SECONDS);
        int maxReceiveCount = PropertyStore.getInt(
            "vf.aws.sqs.lambdaResults.maxReceiveCount", DEFAULT_MAX_RECEIVE_COUNT);
        if (maxReceiveCount < 1) {
            throw new ApplicationFailureException(
                "vf.aws.sqs.lambdaResults.maxReceiveCount must be positive: " + maxReceiveCount);
        }

        return SqsSupport.makeSqsQueue(Lambda.SQS_RESULT_QUEUE, 0, retention, visibilityTimeout,
            deadLetterQueueArn, maxReceiveCount);
    }

    private static int seconds(DurationProp property, String label, int min, int max) {
        Duration value = PropertyStore.getDuration(property);
        long seconds = value.toSeconds();
        if (!value.equals(Duration.ofSeconds(seconds)) || (seconds < min) || (seconds > max)) {
            throw new ApplicationFailureException(
                label + " must be a whole number of seconds between " + min + "s and " + max + "s: " + value);
        }
        return Math.toIntExact(seconds);
    }
}
