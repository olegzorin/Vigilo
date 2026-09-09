package dev.olegz.vf.messaging;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TopicsTest {

    @Test
    void lambdaExecutionTopicsUseLambdaNames() {
        assertEquals("lambda-input", Topics.LAMBDA_INPUT);
        assertEquals("lambda-reset", Topics.LAMBDA_RESET);
        assertEquals("lambda-schedule", Topics.LAMBDA_SCHEDULE);
        assertEquals("lambda-invoke-request", Topics.LAMBDA_INVOKE_REQUEST);
        assertEquals("lambda-run-completion", Topics.LAMBDA_RUN_COMPLETION);
    }

    /**
     * Topic names are routed on the wire, so a duplicated value would silently cross two streams. This guards the
     * constants (e.g. the easily-confused SCHEDULER_* pair) against accidental collisions.
     */
    @Test
    void topicNamesAreUniqueAndNonBlank() throws IllegalAccessException {
        Map<String, String> valueToConstant = new HashMap<>();

        for (Field field : Topics.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) continue;

            String value = (String) field.get(null);
            assertFalse(value == null || value.isBlank(), field.getName() + " must have a non-blank value");

            String previous = valueToConstant.put(value, field.getName());
            assertTrue(previous == null,
                "duplicate topic value '" + value + "' on " + field.getName() + " and " + previous);
        }
    }

    /**
     * Producers route by {@link Topics#isBroadcast} alone, so broadcast topics must be declared as such and shared
     * work queues must not. A regression here silently sends broadcasts to a single consumer (or fans a work queue
     * out to every consumer).
     */
    @Test
    void broadcastTopicsAreDeclared() {
        assertTrue(Topics.isBroadcast(Topics.CACHE_INVALIDATION), "CACHE_INVALIDATION is consumed PER_LISTENER");

        assertFalse(Topics.isBroadcast(Topics.OPERATIONS), "OPERATIONS is a shared work queue");
        assertFalse(Topics.isBroadcast("no-such-topic"), "unknown topics default to shared, not broadcast");
    }
}
