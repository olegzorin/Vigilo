package dev.olegz.vf.aws.sqs;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.olegz.vf.aws.AwsResourceNames;
import dev.olegz.vf.aws.client.AwsClients;
import dev.olegz.vf.aws.error.AwsExceptions;
import dev.olegz.vf.common.objectmap.StringMapper;
import dev.olegz.vf.common.util.CollectionOps;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;

public final class SqsSupport {
    private SqsSupport() {
    }

    /**
     * The shared {@link SqsClient}, built lazily on first use and held for the JVM lifetime;
     * rebuilt if {@link #shutdown()} closed it (e.g. between test contexts). In local mode
     * {@link AwsClients#sqsClient} ignores the HTTP client and returns a {@code LocalSqsClient}.
     */
    private static final AwsClients.LazyClient<SqsClient> CLIENT = AwsClients.lazyClient("SQS", AwsClients::sqsClient);

    private static SqsClient client() {
        return CLIENT.get();
    }

    /** Idempotent: closes the shared client; safe to call more than once. */
    public static void shutdown() {
        CLIENT.shutdown();
    }

    private static final ConcurrentHashMap<String, String> queueArns = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> queueUrls = new ConcurrentHashMap<>();

    /**
     * Idempotent provisioning of SQS queues: returns (and caches) the queue ARN, creates the queue
     * when absent, and reconciles the requested attributes when it already exists.
     *
     * @param queueName         queue name
     * @param delay             delivery delay, seconds
     * @param messageRetention  message retention period, seconds
     * @param visibilityTimeout message visibility timeout, seconds
     * @return queue ARN
     */
    public static String makeSqsQueue(String queueName, int delay, int messageRetention, int visibilityTimeout) {
        return makeSqsQueue(queueName, delay, messageRetention, visibilityTimeout, null, 0);
    }

    /**
     * Idempotently provisions an SQS queue with a dead-letter redrive policy. Attributes are
     * reconciled even when the queue already exists.
     *
     * @param queueName queue name
     * @param delay delivery delay, seconds
     * @param messageRetention message retention period, seconds
     * @param visibilityTimeout message visibility timeout, seconds
     * @param deadLetterQueueArn dead-letter queue ARN
     * @param maxReceiveCount number of receives before SQS moves a message to the dead-letter queue
     * @return queue ARN
     */
    public static String makeSqsQueue(String queueName, int delay, int messageRetention, int visibilityTimeout,
        String deadLetterQueueArn, int maxReceiveCount)
    {
        String resourceName = AwsResourceNames.prefixed(queueName);
        try {
            Map<QueueAttributeName, String> attributes = queueAttributes(
                delay, messageRetention, visibilityTimeout, deadLetterQueueArn, maxReceiveCount);
            String queueUrl = queueUrl(resourceName, attributes);
            client().setQueueAttributes(req -> req.queueUrl(queueUrl).attributes(attributes));

            return queueArns.computeIfAbsent(resourceName, _ -> {
                var arnAttr = QueueAttributeName.QUEUE_ARN;
                var req = GetQueueAttributesRequest.builder().queueUrl(queueUrl).attributeNames(arnAttr).build();
                return client().getQueueAttributes(req).attributes().get(arnAttr);
            });
        } catch (Exception e) {
            throw AwsExceptions.wrapAwsException(e, "Exception provisioning sqs queue " + resourceName);
        }
    }

    public static void sendMessage(String queueName, String body, int delay, int messageRetention, int visibilityTimeout) {
        try {
            queueName = AwsResourceNames.prefixed(queueName);
            String queueUrl = queueUrl(queueName, baseQueueAttributes(delay, messageRetention, visibilityTimeout));
            client().sendMessage(req -> req.queueUrl(queueUrl).messageBody(body));
        } catch (Exception e) {
            throw AwsExceptions.wrapAwsException(e, "Exception in sending sqs message to " + queueName);
        }
    }

    public static List<SqsMessage> receiveMessages(String queueName, int maxMessages, int waitTimeSeconds,
        int visibilityTimeout)
    {
        try {
            queueName = AwsResourceNames.prefixed(queueName);
            String queueUrl = queueUrl(queueName, baseQueueAttributes(0, 345600, visibilityTimeout));
            var messages = client().receiveMessage(req -> req
                .queueUrl(queueUrl)
                .maxNumberOfMessages(maxMessages)
                .waitTimeSeconds(waitTimeSeconds)
                .visibilityTimeout(visibilityTimeout)
                .messageSystemAttributeNames(MessageSystemAttributeName.SENT_TIMESTAMP)).messages();
            if (messages == null) return List.of();
            return CollectionOps.map(messages, m -> {
                    String sent = m.attributes().get(MessageSystemAttributeName.SENT_TIMESTAMP);
                    long sentTimestamp = sent == null ? System.currentTimeMillis() : Long.parseLong(sent);
                    return new SqsMessage(m.body(), m.receiptHandle(), sentTimestamp);
                });
        } catch (Exception e) {
            throw AwsExceptions.wrapAwsException(e, "Exception in receiving sqs messages from " + queueName);
        }
    }

    public static void deleteMessage(String queueName, String receiptHandle) {
        try {
            queueName = AwsResourceNames.prefixed(queueName);
            String queueUrl = queueUrl(queueName, baseQueueAttributes(0, 345600, 30));
            client().deleteMessage(req -> req.queueUrl(queueUrl).receiptHandle(receiptHandle));
        } catch (Exception e) {
            throw AwsExceptions.wrapAwsException(e, "Exception in deleting sqs message from " + queueName);
        }
    }

    static Map<QueueAttributeName, String> queueAttributes(int delay, int messageRetention, int visibilityTimeout,
        String deadLetterQueueArn, int maxReceiveCount)
    {
        Map<QueueAttributeName, String> attributes = new EnumMap<>(QueueAttributeName.class);
        attributes.putAll(baseQueueAttributes(delay, messageRetention, visibilityTimeout));
        if (deadLetterQueueArn != null) {
            if (maxReceiveCount < 1) throw new IllegalArgumentException("maxReceiveCount must be positive");
            attributes.put(QueueAttributeName.REDRIVE_POLICY, StringMapper.toString(Map.of(
                "deadLetterTargetArn", deadLetterQueueArn,
                "maxReceiveCount", Integer.toString(maxReceiveCount))));
        }
        return attributes;
    }

    private static Map<QueueAttributeName, String> baseQueueAttributes(
        int delay, int messageRetention, int visibilityTimeout)
    {
        return Map.of(
            QueueAttributeName.DELAY_SECONDS, Integer.toString(delay),
            QueueAttributeName.MESSAGE_RETENTION_PERIOD, Integer.toString(messageRetention),
            QueueAttributeName.VISIBILITY_TIMEOUT, Integer.toString(visibilityTimeout));
    }

    private static String queueUrl(String queueName, Map<QueueAttributeName, String> attributes) {
        return queueUrls.computeIfAbsent(queueName, k -> {
            try {
                try {
                    return client().getQueueUrl(req -> req.queueName(queueName)).queueUrl();
                } catch (QueueDoesNotExistException e) {
                    return client().createQueue(req -> req.queueName(queueName).attributes(attributes)).queueUrl();
                }
            } catch (Exception e) {
                throw AwsExceptions.wrapAwsException(e, "Exception in getting sqs queue url " + queueName);
            }
        });
    }
}
