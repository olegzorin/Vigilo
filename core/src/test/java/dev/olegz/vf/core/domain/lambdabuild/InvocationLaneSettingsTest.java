package dev.olegz.vf.core.domain.lambdabuild;

import dev.olegz.vf.common.props.DurationProp;
import dev.olegz.vf.common.props.IntProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class InvocationLaneSettingsTest {
    @BeforeAll
    static void startPropertyStore() {
        PropertyStore.start();
    }

    @Test
    void resolvesConfiguredSettingsForEachLane() {
        InvocationLaneSettings defaultSettings = InvocationLaneSettings.forLane(InvocationLane.DEFAULT);
        InvocationLaneSettings asyncSettings = InvocationLaneSettings.forLane(InvocationLane.ASYNC);

        assertAll(
            () -> assertEquals(memory(IntProp.LAMBDA_MEMORY_SIZE_MB), defaultSettings.memorySize()),
            () -> assertEquals(timeout(DurationProp.LAMBDA_TIMEOUT), defaultSettings.timeout()),
            () -> assertEquals(memory(IntProp.LAMBDA_ASYNC_MEMORY_SIZE_MB), asyncSettings.memorySize()),
            () -> assertEquals(timeout(DurationProp.LAMBDA_ASYNC_TIMEOUT), asyncSettings.timeout())
        );
    }

    private static int memory(IntProp property) {
        return Math.clamp(PropertyStore.getInt(property), 128, 10240);
    }

    private static int timeout(DurationProp property) {
        return Math.clamp(Math.toIntExact(PropertyStore.getDuration(property).toSeconds()), 1, 900);
    }
}
