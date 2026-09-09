package dev.olegz.vf.aws.sqs;

/**
 * Broker-facing SQS message data without exposing AWS SDK model classes outside the aws module.
 */
public record SqsMessage(String body, String receiptHandle, long sentTimestamp) {
}
