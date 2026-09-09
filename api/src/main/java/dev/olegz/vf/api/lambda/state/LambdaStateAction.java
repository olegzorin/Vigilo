package dev.olegz.vf.api.lambda.state;

import java.time.Instant;
import java.util.Map;

import dev.olegz.vf.api.device.ApiDeviceCurrentState;
import dev.olegz.vf.api.location.ApiLocationCurrentState;
import dev.olegz.vf.api.web.support.ActionResponse;
import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.registry.domain.account.LocationCurrentState;
import dev.olegz.vf.registry.domain.device.DeviceCurrentState;
import dev.olegz.vf.core.service.lambda.LambdaClientService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;

@Component
public class LambdaStateAction {
    private final LambdaClientService lambdaClientService;

    public LambdaStateAction(LambdaClientService lambdaClientService) {
        this.lambdaClientService = lambdaClientService;
    }

    public LocationStateResponse updateLocationState(
        String lambdaApiKey,
        int locationId,
        UpdateLocationStateRequest request)
    {
        LocationCurrentState currentState =
            lambdaClientService.updateLocationCurrentState(lambdaApiKey, locationId, request.state);

        LocationStateResponse response = new LocationStateResponse();
        response.currentState = new ApiLocationCurrentState(currentState);
        return response;
    }

    public DeviceStateResponse updateDeviceState(
        String lambdaApiKey,
        String deviceUuid,
        UpdateDeviceStateRequest request)
    {
        DeviceCurrentState currentState = lambdaClientService.updateDeviceCurrentState(
            lambdaApiKey,
            deviceUuid,
            request.state,
            new Datetime(request.measuredAt.toEpochMilli()));

        DeviceStateResponse response = new DeviceStateResponse();
        response.currentState = new ApiDeviceCurrentState(currentState);
        return response;
    }

    public static class UpdateLocationStateRequest {
        public @NotBlank(message = "state") @Size(max = 50, message = "state") String state;
    }

    public static class UpdateDeviceStateRequest {
        public @NotNull(message = "state") Map<String, Object> state;
        public @NotNull(message = "measuredAt") Instant measuredAt;
    }

    public static class LocationStateResponse extends ActionResponse {
        public ApiLocationCurrentState currentState;
    }

    public static class DeviceStateResponse extends ActionResponse {
        public ApiDeviceCurrentState currentState;
    }
}
