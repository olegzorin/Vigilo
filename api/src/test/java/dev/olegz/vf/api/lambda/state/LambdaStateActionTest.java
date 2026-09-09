package dev.olegz.vf.api.lambda.state;

import java.util.Map;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.objectmap.StringMapper;
import dev.olegz.vf.registry.domain.account.LocationCurrentState;
import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaAssignment;
import dev.olegz.vf.core.domain.lambdarun.LambdaKey;
import dev.olegz.vf.core.domain.lambdarun.LambdaKeyInput;
import dev.olegz.vf.core.domain.lambdarun.LambdaRuntimeAssignment;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import dev.olegz.vf.registry.domain.device.DeviceCurrentState;
import dev.olegz.vf.core.event.TriggerEvent;
import dev.olegz.vf.core.service.lambda.LambdaClientService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LambdaStateActionTest {

    @Test
    void delegatesCurrentStateUpdatesToLambdaClientService() {
        FakeLambdaClientService lambdaClientService = new FakeLambdaClientService();
        LambdaStateAction action = new LambdaStateAction(lambdaClientService);

        LambdaStateAction.UpdateLocationStateRequest locationRequest =
            new LambdaStateAction.UpdateLocationStateRequest();
        locationRequest.state = "HOME";
        LambdaStateAction.LocationStateResponse locationResponse =
            action.updateLocationState("lambda-key", 11, locationRequest);

        Datetime measuredAt = Datetime.nowMinusDays(1);
        LambdaStateAction.UpdateDeviceStateRequest deviceRequest =
            new LambdaStateAction.UpdateDeviceStateRequest();
        deviceRequest.state = Map.of("status", "online");
        deviceRequest.measuredAt = measuredAt.toInstant();
        LambdaStateAction.DeviceStateResponse deviceResponse =
            action.updateDeviceState("lambda-key", "device-1", deviceRequest);

        assertEquals("lambda-key", lambdaClientService.appKey);
        assertEquals(11, lambdaClientService.locationId);
        assertEquals("HOME", locationResponse.currentState.state);
        assertEquals("device-1", lambdaClientService.deviceId);
        assertEquals(Map.of("status", "online"), lambdaClientService.deviceState);
        assertEquals(measuredAt, lambdaClientService.measuredAt);
        assertEquals(Map.of("status", "online"), deviceResponse.currentState.state);
    }

    @Test
    void deviceStateRequestDeserializesDocumentedTimestamp() {
        LambdaStateAction.UpdateDeviceStateRequest request = StringMapper.readValue(
            """
            {
              "state": {"status": "online"},
              "measuredAt": "2026-07-24T12:00:00Z"
            }
            """,
            LambdaStateAction.UpdateDeviceStateRequest.class);

        assertEquals(Map.of("status", "online"), request.state);
        assertNotNull(request.measuredAt);
    }

    private static class FakeLambdaClientService implements LambdaClientService {
        private String appKey;
        private int locationId;
        private String deviceId;
        private Map<String, Object> deviceState;
        private Datetime measuredAt;

        @Override
        public LocationCurrentState updateLocationCurrentState(
            String appKey,
            int locationId,
            String state)
        {
            this.appKey = appKey;
            this.locationId = locationId;
            LocationCurrentState currentState = new LocationCurrentState();
            currentState.locationId = locationId;
            currentState.state = state;
            currentState.stateDate = Datetime.now();
            return currentState;
        }

        @Override
        public DeviceCurrentState updateDeviceCurrentState(
            String appKey,
            String deviceId,
            Map<String, Object> state,
            Datetime measuredAt)
        {
            this.appKey = appKey;
            this.deviceId = deviceId;
            this.deviceState = state;
            this.measuredAt = measuredAt;
            DeviceCurrentState currentState = new DeviceCurrentState();
            currentState.deviceUuid = deviceId;
            currentState.state = state;
            currentState.measuredAt = measuredAt;
            currentState.receivedAt = Datetime.now();
            return currentState;
        }

        @Override public LambdaKeyInput createLambdaKey(
            LambdaRuntimeAssignment lambda, long expiry, InvocationLane lane, int triggers)
        {
            throw new UnsupportedOperationException();
        }
        @Override public LambdaKeyInput generateLambdaApiKey(User user, int lambdaAssignmentId) {
            throw new UnsupportedOperationException();
        }
        @Override public LambdaKey parseLambdaKey(String appKey) { throw new UnsupportedOperationException(); }
        @Override public boolean checkLambdaLocationAccess(LambdaKey key, int locationId) { return false; }
        @Override public boolean checkLambdaDeviceAccess(LambdaKey key, String deviceId) { return false; }
        @Override public byte[] getVariable(String appKey, String name, boolean shared) {
            throw new UnsupportedOperationException();
        }
        @Override public void putVariable(String appKey, String name, boolean shared, byte[] value) {
            throw new UnsupportedOperationException();
        }
        @Override public void deleteVariable(String appKey, String name, boolean shared) {
            throw new UnsupportedOperationException();
        }
        @Override public void sendResetEvent(LambdaAssignment lambdaAssignment) { throw new UnsupportedOperationException(); }
        @Override public void publishTriggerEvent(TriggerEvent event) { throw new UnsupportedOperationException(); }
        @Override public void dispatchExpiredLambdaRunCompletions() { throw new UnsupportedOperationException(); }
        @Override public boolean startRun(
            LambdaKey lambdaKey, long invocationToken, String awsRequestId, String logStreamName)
        {
            throw new UnsupportedOperationException();
        }
        @Override public void triggerScheduledLambdas(
            java.util.function.Consumer<dev.olegz.vf.core.event.ScheduledEvent> eventConsumer)
        {
            throw new UnsupportedOperationException();
        }
    }
}
