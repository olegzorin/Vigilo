package dev.olegz.vf.core.domain.lambdabuild;

import java.time.Duration;

import dev.olegz.vf.common.props.DurationProp;
import dev.olegz.vf.common.props.IntProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;

public record InvocationLaneSettings(int memorySize, int timeout) {
    private static final int MIN_MEMORY_SIZE = 128;
    private static final int MAX_MEMORY_SIZE = 10240;
    private static final Duration MIN_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration MAX_TIMEOUT = Duration.ofSeconds(900);

    public static InvocationLaneSettings forLane(InvocationLane lane) {
        return switch (lane) {
            case DEFAULT -> fromProperties(IntProp.LAMBDA_MEMORY_SIZE_MB, DurationProp.LAMBDA_TIMEOUT);
            case ASYNC -> fromProperties(IntProp.LAMBDA_ASYNC_MEMORY_SIZE_MB, DurationProp.LAMBDA_ASYNC_TIMEOUT);
        };
    }

    private static InvocationLaneSettings fromProperties(IntProp memoryProperty, DurationProp timeoutProperty) {
        int memorySize = Math.clamp(PropertyStore.getInt(memoryProperty), MIN_MEMORY_SIZE, MAX_MEMORY_SIZE);
        int timeout = Math.toIntExact(Math.clamp(PropertyStore.getDuration(timeoutProperty).toSeconds(), MIN_TIMEOUT.toSeconds(), MAX_TIMEOUT.toSeconds()));
        return new InvocationLaneSettings(memorySize, timeout);
    }
}
