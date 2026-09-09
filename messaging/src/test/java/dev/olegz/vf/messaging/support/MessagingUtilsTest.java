package dev.olegz.vf.messaging.support;

import java.util.UUID;

import dev.olegz.vf.common.RuntimeIdentity;
import dev.olegz.vf.messaging.consumer.ConsumerGroupScope;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessagingUtilsTest {

    @Test
    void groupNamesUseIdentityMatchingTheirScope() {
        String topic = "test-topic";

        assertEquals(topic, MessagingUtils.makeGroupName(topic, ConsumerGroupScope.SHARED));
        assertEquals(RuntimeIdentity.NODE_ID + '-' + topic,
            MessagingUtils.makeGroupName(topic, ConsumerGroupScope.PER_SERVER));

        String firstListenerGroup = MessagingUtils.makeGroupName(topic, ConsumerGroupScope.PER_LISTENER);
        String secondListenerGroup = MessagingUtils.makeGroupName(topic, ConsumerGroupScope.PER_LISTENER);

        UUID.fromString(RuntimeIdentity.INSTANCE_ID);
        assertTrue(firstListenerGroup.startsWith(RuntimeIdentity.INSTANCE_ID + '-' + topic + '-'));
        assertTrue(secondListenerGroup.startsWith(RuntimeIdentity.INSTANCE_ID + '-' + topic + '-'));
        assertNotEquals(firstListenerGroup, secondListenerGroup);
    }
}
