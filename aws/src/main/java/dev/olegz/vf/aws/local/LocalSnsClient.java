package dev.olegz.vf.aws.local;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.olegz.vf.aws.client.AwsClients;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.*;

/**
 * Local in-memory implementation of {@link SnsClient}, covering the topic <em>provisioning</em>,
 * <em>publish</em>, and <em>subscribe</em> calls used by {@code dev.olegz.vf.aws.sns.SnsSupport}
 * (resolving a topic's ARN, creating it on demand, publishing a message, and registering a filtered
 * subscription). No real fan-out or filter evaluation happens: publish returns a synthetic message
 * id and subscribe records the subscription so its filter policy can be inspected, mirroring how
 * {@link LocalSqsClient} handles queue provisioning without transporting messages.
 */
public final class LocalSnsClient implements SnsClient {

    private final ConcurrentHashMap<String, String> topicArns = new ConcurrentHashMap<>();
    /** subscription ARN -> {@code FilterPolicy} attribute value (or empty string if none). */
    private final ConcurrentHashMap<String, String> subscriptionFilterPolicies = new ConcurrentHashMap<>();

    @Override
    public CreateTopicResponse createTopic(CreateTopicRequest request) {
        String arn = topicArns.computeIfAbsent(request.name(), LocalSnsClient::topicArn);
        return CreateTopicResponse.builder().topicArn(arn).build();
    }

    @Override
    public PublishResponse publish(PublishRequest request) {
        return PublishResponse.builder().messageId(UUID.randomUUID().toString()).build();
    }

    @Override
    public SubscribeResponse subscribe(SubscribeRequest request) {
        String subscriptionArn = request.topicArn() + ':' + UUID.randomUUID();
        subscriptionFilterPolicies.put(subscriptionArn,
            request.attributes().getOrDefault("FilterPolicy", ""));
        return SubscribeResponse.builder().subscriptionArn(subscriptionArn).build();
    }

    /** The {@code FilterPolicy} recorded for a subscription, or {@code null} if unknown. Test-only. */
    public String filterPolicyOf(String subscriptionArn) {
        return subscriptionFilterPolicies.get(subscriptionArn);
    }

    private static String topicArn(String topicName) {
        return "arn:aws:sns:" + AwsClients.REGION.id() + ':' + LocalAws.ACCOUNT_ID + ':' + topicName;
    }

    @Override
    public String serviceName() {
        return SnsClient.SERVICE_NAME;
    }

    @Override
    public void close() {
    }
}
