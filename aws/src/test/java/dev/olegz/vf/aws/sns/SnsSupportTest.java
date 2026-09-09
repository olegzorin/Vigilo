package dev.olegz.vf.aws.sns;

import java.util.Map;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link SnsSupport#numericFilterPolicy}, which builds the JSON filter policy that
 * makes a subscription match a numeric message attribute against accepted values.
 */
class SnsSupportTest {

    @Test
    void singleValuePolicy() {
        assertEquals("{\"priority\":[3]}", SnsSupport.numericFilterPolicy("priority", 3));
    }

    @Test
    void multiValuePolicy() {
        assertEquals("{\"priority\":[1,3,5]}", SnsSupport.numericFilterPolicy("priority", 1, 3, 5));
    }

    @Test
    void numericMessageAttributesSupportCombinedSubscriptionFilters() {
        Map<String, MessageAttributeValue> attributes = SnsSupport.numericMessageAttributes(Map.of(
            "devTeamId", 17,
            "lambdaId", 42));

        assertEquals("Number", attributes.get("devTeamId").dataType());
        assertEquals("17", attributes.get("devTeamId").stringValue());
        assertEquals("Number", attributes.get("lambdaId").dataType());
        assertEquals("42", attributes.get("lambdaId").stringValue());
    }
}
