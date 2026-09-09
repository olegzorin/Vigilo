package dev.olegz.vf.aws.sns;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.olegz.vf.aws.AwsResourceNames;
import dev.olegz.vf.aws.client.AwsClients;
import dev.olegz.vf.aws.error.AwsExceptions;
import dev.olegz.vf.common.objectmap.StringMapper;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;

public final class SnsSupport {
    private SnsSupport() {
    }

    /** SNS message-attribute {@code DataType} that makes an attribute eligible for numeric filtering. */
    private static final String NUMBER_DATA_TYPE = "Number";

    /**
     * The shared {@link SnsClient}, built lazily on first use and held for the JVM lifetime;
     * rebuilt if {@link #shutdown()} closed it (e.g. between test contexts). In local mode
     * {@link AwsClients#snsClient} returns a {@code LocalSnsClient}.
     */
    private static final class ClientHolder {
        private static final AwsClients.LazyClient<SnsClient> CLIENT =
            AwsClients.lazyClient("SNS", AwsClients::snsClient);
    }

    private static SnsClient client() {
        return ClientHolder.CLIENT.get();
    }

    /** Idempotent: closes the shared client; safe to call more than once. */
    public static void shutdown() {
        ClientHolder.CLIENT.shutdown();
    }

    private static final ConcurrentHashMap<String, String> topicArns = new ConcurrentHashMap<>();

    /**
     * Idempotent provisioning of SNS topics: returns (and caches) the topic ARN, creating the topic
     * if it does not yet exist. {@code createTopic} is itself idempotent in SNS for a standard topic
     * of the same name, so this is safe to call repeatedly.
     *
     * @param topicName topic name
     * @return topic ARN
     */
    public static String makeSnsTopic(String topicName) {
        String resourceName = AwsResourceNames.prefixed(topicName);
        SnsClient c = client();
        return topicArns.computeIfAbsent(resourceName, _ -> {
            try {
                return c.createTopic(req -> req.name(resourceName)).topicArn();
            } catch (Exception e) {
                throw AwsExceptions.wrapAwsException(e, "Exception in creating sns topic " + resourceName);
            }
        });
    }

    /**
     * Publishes a message to the given topic ARN.
     *
     * @param topicArn topic ARN
     * @param message  message body
     * @return the published message id
     */
    public static String publish(String topicArn, String message) {
        return publish(topicArn, null, message);
    }

    /**
     * Publishes a message to the given topic ARN, with an optional subject (used by email
     * subscriptions and available to filter policies).
     *
     * @param topicArn topic ARN
     * @param subject  message subject, or {@code null} for none
     * @param message  message body
     * @return the published message id
     */
    public static String publish(String topicArn, String subject, String message) {
        PublishRequest.Builder req = PublishRequest.builder().topicArn(topicArn).message(message);
        if (subject != null) req.subject(subject);
        return publish(req);
    }

    /**
     * Publishes a message tagged with a numeric ({@code int}) message attribute, so that
     * subscriptions carrying a numeric filter policy on {@code attributeName} (see
     * {@link #subscribeWithNumericFilter}) receive it only when {@code attributeValue} matches.
     *
     * @param topicArn       topic ARN
     * @param subject        message subject, or {@code null} for none
     * @param message        message body
     * @param attributeName  message-attribute name the subscriber filters on
     * @param attributeValue numeric value of that attribute
     * @return the published message id
     */
    public static String publish(String topicArn, String subject, String message,
                                 String attributeName, int attributeValue) {
        return publish(topicArn, subject, message, Map.of(attributeName, attributeValue));
    }

    /**
     * Publishes a message tagged with numeric message attributes. SNS subscription filter policies
     * can match any individual attribute or combine several attributes in the same policy.
     *
     * @param topicArn         topic ARN
     * @param subject          message subject, or {@code null} for none
     * @param message          message body
     * @param numericAttributes message-attribute names and numeric values
     * @return the published message id
     */
    public static String publish(String topicArn, String subject, String message,
                                 Map<String, Integer> numericAttributes) {
        PublishRequest.Builder req = PublishRequest.builder()
            .topicArn(topicArn)
            .message(message)
            .messageAttributes(numericMessageAttributes(numericAttributes));
        if (subject != null) req.subject(subject);
        return publish(req);
    }

    static Map<String, MessageAttributeValue> numericMessageAttributes(Map<String, Integer> attributes) {
        Map<String, MessageAttributeValue> messageAttributes = new HashMap<>(attributes.size());
        attributes.forEach((name, value) -> messageAttributes.put(name,
            MessageAttributeValue.builder()
                .dataType(NUMBER_DATA_TYPE)
                .stringValue(Integer.toString(value))
                .build()));
        return messageAttributes;
    }

    private static String publish(PublishRequest.Builder req) {
        try {
            return client().publish(req.build()).messageId();
        } catch (Exception e) {
            throw AwsExceptions.wrapAwsException(e, "Exception in publishing to sns topic " + req.build().topicArn());
        }
    }

    /**
     * Subscribes an endpoint to a topic and attaches a filter policy that matches the numeric
     * message attribute {@code attributeName} against {@code acceptedValues} (numeric equality).
     * The subscriber then receives only messages published with that attribute set to one of the
     * accepted values (see {@link #publish(String, String, String, String, int)}); messages missing
     * the attribute or carrying a non-matching value are dropped by SNS before delivery.
     *
     * @param topicArn       topic ARN
     * @param protocol       delivery protocol (e.g. {@code "sqs"}, {@code "lambda"}, {@code "https"})
     * @param endpoint       endpoint for the protocol (e.g. the SQS queue ARN)
     * @param attributeName  numeric message-attribute name to filter on
     * @param acceptedValues values the attribute may take for the message to be delivered
     * @return the subscription ARN
     */
    public static String subscribeWithNumericFilter(String topicArn, String protocol, String endpoint,
                                                    String attributeName, int... acceptedValues) {
        String filterPolicy = numericFilterPolicy(attributeName, acceptedValues);
        try {
            SubscribeRequest req = SubscribeRequest.builder()
                .topicArn(topicArn)
                .protocol(protocol)
                .endpoint(endpoint)
                .returnSubscriptionArn(true)
                .attributes(Map.of("FilterPolicy", filterPolicy))
                .build();
            return client().subscribe(req).subscriptionArn();
        } catch (Exception e) {
            throw AwsExceptions.wrapAwsException(e, "Exception in subscribing " + endpoint + " to sns topic " + topicArn);
        }
    }

    /**
     * Builds an SNS subscription filter policy matching the message attribute {@code attributeName}
     * against any of {@code acceptedValues} by numeric equality, e.g. {@code {"priority":[1,3]}}.
     */
    static String numericFilterPolicy(String attributeName, int... acceptedValues) {
        List<Integer> values = new ArrayList<>(acceptedValues.length);
        for (int v : acceptedValues) {
            values.add(v);
        }
        return StringMapper.toString(Map.of(attributeName, values));
    }
}
