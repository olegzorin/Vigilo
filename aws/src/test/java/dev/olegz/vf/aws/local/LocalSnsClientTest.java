package dev.olegz.vf.aws.local;

import java.util.Map;

import dev.olegz.vf.aws.LocalAwsTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LocalSnsClient}: topic provisioning is idempotent (stable ARN), publish
 * returns a message id, and a filtered subscription records its filter policy — all without real AWS.
 */
class LocalSnsClientTest extends LocalAwsTestSupport {

    @Test
    void createTopicIsIdempotentAndReturnsArn() {
        LocalSnsClient client = new LocalSnsClient();

        String arn = client.createTopic(r -> r.name("alerts")).topicArn();
        assertNotNull(arn);
        assertTrue(arn.startsWith("arn:aws:sns:"), arn);
        assertTrue(arn.endsWith(":alerts"), arn);

        // same name -> same ARN
        assertEquals(arn, client.createTopic(r -> r.name("alerts")).topicArn());
    }

    @Test
    void publishWithNumericAttributeReturnsMessageId() {
        LocalSnsClient client = new LocalSnsClient();
        String arn = client.createTopic(r -> r.name("alerts")).topicArn();

        MessageAttributeValue priority = MessageAttributeValue.builder()
            .dataType("Number").stringValue("3").build();
        String id = client.publish(r -> r.topicArn(arn).message("body")
            .messageAttributes(Map.of("priority", priority))).messageId();

        assertNotNull(id);
    }

    @Test
    void subscribeRecordsFilterPolicy() {
        LocalSnsClient client = new LocalSnsClient();
        String arn = client.createTopic(r -> r.name("alerts")).topicArn();

        String subArn = client.subscribe(r -> r.topicArn(arn).protocol("sqs").endpoint("queue-arn")
            .attributes(Map.of("FilterPolicy", "{\"priority\":[3]}"))).subscriptionArn();

        assertTrue(subArn.startsWith(arn + ':'), subArn);
        assertEquals("{\"priority\":[3]}", client.filterPolicyOf(subArn));
    }
}
