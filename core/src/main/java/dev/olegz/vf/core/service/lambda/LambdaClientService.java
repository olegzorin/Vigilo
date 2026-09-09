package dev.olegz.vf.core.service.lambda;

import java.util.Map;
import java.util.function.Consumer;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.exception.InvalidJwtException;
import dev.olegz.vf.registry.domain.account.LocationCurrentState;
import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaAssignment;
import dev.olegz.vf.core.domain.lambdarun.LambdaKey;
import dev.olegz.vf.core.domain.lambdarun.LambdaKeyInput;
import dev.olegz.vf.core.domain.lambdarun.LambdaRuntimeAssignment;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import dev.olegz.vf.registry.domain.device.DeviceCurrentState;
import dev.olegz.vf.core.event.ScheduledEvent;
import dev.olegz.vf.core.event.TriggerEvent;

public interface LambdaClientService {
    LambdaKeyInput createLambdaKey(LambdaRuntimeAssignment lambda, long expiry, InvocationLane lane, int triggers);

    LambdaKeyInput generateLambdaApiKey(User user, int lambdaAssignmentId);

    LambdaKey parseLambdaKey(String appKey) throws InvalidJwtException;

    boolean checkLambdaLocationAccess(LambdaKey key, int locationId);

    boolean checkLambdaDeviceAccess(LambdaKey key, String deviceId);

    LocationCurrentState updateLocationCurrentState(String appKey, int locationId, String state);

    DeviceCurrentState updateDeviceCurrentState(
        String appKey,
        String deviceId,
        Map<String, Object> state,
        Datetime measuredAt);

    byte[] getVariable(String appKey, String name, boolean shared);

    void putVariable(String appKey, String name, boolean shared, byte[] value);

    void deleteVariable(String appKey, String name, boolean shared);

    void sendResetEvent(LambdaAssignment lambdaAssignment);

    void publishTriggerEvent(TriggerEvent event);

    void dispatchExpiredLambdaRunCompletions();

    boolean startRun(LambdaKey lambdaKey, long invocationToken, String awsRequestId, String logStreamName);

    void triggerScheduledLambdas(Consumer<ScheduledEvent> eventConsumer);

}
