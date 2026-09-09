package dev.olegz.vf.api.device;

import dev.olegz.vf.api.lambda.state.LambdaStateAction;
import dev.olegz.vf.api.web.ApiHeaders;
import dev.olegz.vf.api.web.support.ActionContextFactory;
import dev.olegz.vf.api.web.support.ActionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import static dev.olegz.vf.api.web.ApiHeaders.API_KEY;

@RestController("deviceController")
@RequestMapping(path = "/vf/devices", produces = MediaType.APPLICATION_JSON_VALUE)
public class DeviceController {
    private final DeviceAction deviceAction;
    private final LambdaStateAction lambdaStateAction;
    private final ActionContextFactory contextFactory;

    public DeviceController(
        DeviceAction deviceAction,
        LambdaStateAction lambdaStateAction,
        ActionContextFactory contextFactory)
    {
        this.deviceAction = deviceAction;
        this.lambdaStateAction = lambdaStateAction;
        this.contextFactory = contextFactory;
    }

    @RequestMapping(method = RequestMethod.GET)
    public ActionResponse listDevices(
        @RequestHeader(API_KEY) String key,
        @RequestParam(required = false) @Positive Integer typeId,
        @RequestParam(required = false) @Positive Integer locationId)
    {
        return deviceAction.listDevices(contextFactory.current(), typeId, locationId);
    }

    @RequestMapping(method = RequestMethod.GET, path = "{deviceUuid}")
    public ActionResponse getDevice(
        @RequestHeader(API_KEY) String key,
        @PathVariable String deviceUuid)
    {
        return deviceAction.getDevice(contextFactory.current(), deviceUuid);
    }

    @RequestMapping(method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ActionResponse createDevice(
        @RequestHeader(API_KEY) String key,
        @Valid @RequestBody DeviceAction.CreateDeviceRequest request)
    {
        return deviceAction.createDevice(contextFactory.current(), request);
    }

    @RequestMapping(method = RequestMethod.PUT, path = "{deviceUuid}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ActionResponse updateDevice(
        @RequestHeader(API_KEY) String key,
        @PathVariable String deviceUuid,
        @Valid @RequestBody DeviceAction.UpdateDeviceRequest request)
    {
        return deviceAction.updateDevice(contextFactory.current(), deviceUuid, request);
    }

    @RequestMapping(
        method = RequestMethod.PUT,
        path = "{deviceUuid}/current-state",
        consumes = MediaType.APPLICATION_JSON_VALUE)
    public ActionResponse updateCurrentState(
        @RequestHeader(ApiHeaders.LAMBDA_API_KEY) String key,
        @PathVariable String deviceUuid,
        @Valid @RequestBody LambdaStateAction.UpdateDeviceStateRequest request)
    {
        return lambdaStateAction.updateDeviceState(key, deviceUuid, request);
    }
}
