package dev.olegz.vf.common.props;

import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PropertyStoreTest {

    @BeforeAll
    static void startPropertyStore() {
        PropertyStore.start();
    }

    @Test
    void getProperty() {
        PropertyStore.set("test.common.value", "value");
        PropertyStore.set("test.common.multiple.line", "part1-part2-part3");
        PropertyStore.set("test.common.int", "123");
        PropertyStore.set("test.common.long", "789");
        PropertyStore.set("test.common.bool", "true");

        assertEquals("value", PropertyStore.getString("test.common.value"));
        assertEquals("part1-part2-part3", PropertyStore.getString("test.common.multiple.line"));
        assertEquals(123, PropertyStore.getInt("test.common.int", 456));
        assertEquals(789L, PropertyStore.getLong("test.common.long", 456));
        assertTrue(PropertyStore.getBoolean("test.common.bool", false));

        String manualProperty = "test.common.manual";
        String manualValue = "manual.value";
        PropertyStore.set(manualProperty, manualValue);
        assertEquals(manualValue, PropertyStore.getString(manualProperty));

        PropertyStore.set(manualProperty, "value1,value2,value3,,");
        List<String> values = PropertyStore.getList(manualProperty);
        assertEquals(List.of("value1", "value2", "value3"), values);
        assertSame(values, PropertyStore.getList(manualProperty));

        PropertyStore.set(manualProperty, "value4, value5");
        List<String> updatedValues = PropertyStore.getList(manualProperty);
        assertEquals(List.of("value4", "value5"), updatedValues);
        assertNotSame(values, updatedValues);
    }

    @Test
    void parsesAndCachesDurationProperties() {
        assertEquals(Duration.ofMillis(2), PropertyStore.parseDuration("2ms"));
        assertEquals(Duration.ofSeconds(3), PropertyStore.parseDuration("3s"));
        assertEquals(Duration.ofMinutes(4), PropertyStore.parseDuration("4m"));
        assertEquals(Duration.ofHours(5), PropertyStore.parseDuration("5h"));
        assertEquals(Duration.ofDays(6), PropertyStore.parseDuration("6d"));

        assertThrows(IllegalArgumentException.class, () -> PropertyStore.parseDuration("2"));
        assertThrows(IllegalArgumentException.class, () -> PropertyStore.parseDuration("-2s"));
        assertThrows(IllegalArgumentException.class, () -> PropertyStore.parseDuration("+2s"));
        assertThrows(IllegalArgumentException.class, () -> PropertyStore.parseDuration("2S"));

        Duration original = PropertyStore.getDuration(DurationProp.LAMBDA_RETRIES_DELAY);
        try {
            PropertyStore.updateTypedProps(Map.of(DurationProp.LAMBDA_RETRIES_DELAY.label, "2ms"));
            assertEquals(Duration.ofMillis(2), PropertyStore.getDuration(DurationProp.LAMBDA_RETRIES_DELAY));
        } finally {
            PropertyStore.set(DurationProp.LAMBDA_RETRIES_DELAY, original);
        }
    }

    @Test
    void encryption() throws GeneralSecurityException {
        String propertyName = "test.common.prop.name";
        String propertyValue = "prop.value";

        String encrypted = PropertyStore.encrypt(propertyValue);
        PropertyStore.set(propertyName, encrypted);
        String decrypted = PropertyStore.decrypt(propertyName);

        assertEquals(propertyValue, decrypted);
    }

}
