package dev.olegz.vf.api.location;

import dev.olegz.vf.api.lambda.state.LambdaStateAction;
import dev.olegz.vf.api.device.DeviceAction;
import dev.olegz.vf.api.web.ApiHeaders;
import dev.olegz.vf.api.web.support.ActionContextFactory;
import dev.olegz.vf.api.web.support.ActionResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import static dev.olegz.vf.api.web.ApiHeaders.API_KEY;

@RestController("locationController")
@RequestMapping(path = "/vf/locations", produces = MediaType.APPLICATION_JSON_VALUE)
public class LocationController {
    private final LocationAction locationAction;
    private final DeviceAction deviceAction;
    private final LambdaStateAction lambdaStateAction;
    private final ActionContextFactory contextFactory;

    public LocationController(
        LocationAction locationAction,
        DeviceAction deviceAction,
        LambdaStateAction lambdaStateAction,
        ActionContextFactory contextFactory)
    {
        this.locationAction = locationAction;
        this.deviceAction = deviceAction;
        this.lambdaStateAction = lambdaStateAction;
        this.contextFactory = contextFactory;
    }

    @RequestMapping(method = RequestMethod.GET)
    public ActionResponse listLocations(@RequestHeader(API_KEY) String key) {
        return locationAction.listLocations(contextFactory.current());
    }

    @RequestMapping(method = RequestMethod.GET, path = "{locationId}")
    public ActionResponse getLocation(@RequestHeader(API_KEY) String key, @PathVariable int locationId) {
        return locationAction.getLocation(contextFactory.current(), locationId);
    }

    @RequestMapping(method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ActionResponse createLocation(
        @RequestHeader(API_KEY) String key,
        @Valid @RequestBody LocationAction.CreateLocationRequest request)
    {
        return locationAction.createLocation(contextFactory.current(), request);
    }

    @RequestMapping(method = RequestMethod.PUT, path = "{locationId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ActionResponse updateLocation(
        @RequestHeader(API_KEY) String key,
        @PathVariable int locationId,
        @Valid @RequestBody LocationAction.UpdateLocationRequest request)
    {
        return locationAction.updateLocation(contextFactory.current(), locationId, request);
    }

    @RequestMapping(method = RequestMethod.DELETE, path = "{locationId}")
    public ActionResponse deleteLocation(@RequestHeader(API_KEY) String key, @PathVariable int locationId) {
        return locationAction.deleteLocation(contextFactory.current(), locationId);
    }

    @RequestMapping(
        method = RequestMethod.PUT,
        path = "{locationId}/current-state",
        consumes = MediaType.APPLICATION_JSON_VALUE)
    public ActionResponse updateCurrentState(
        @RequestHeader(ApiHeaders.LAMBDA_API_KEY) String key,
        @PathVariable int locationId,
        @Valid @RequestBody LambdaStateAction.UpdateLocationStateRequest request)
    {
        return lambdaStateAction.updateLocationState(key, locationId, request);
    }

    @RequestMapping(method = RequestMethod.POST, path = "{locationId}/users/{userId}")
    public ActionResponse assignUser(
        @RequestHeader(API_KEY) String key,
        @PathVariable int locationId,
        @PathVariable int userId)
    {
        return locationAction.assignUser(contextFactory.current(), locationId, userId);
    }

    @RequestMapping(method = RequestMethod.DELETE, path = "{locationId}/users/{userId}")
    public ActionResponse cancelAssignment(
        @RequestHeader(API_KEY) String key,
        @PathVariable int locationId,
        @PathVariable int userId)
    {
        return locationAction.cancelAssignment(contextFactory.current(), locationId, userId);
    }

    @RequestMapping(method = RequestMethod.POST, path = "{locationId}/devices/{deviceUuid}")
    public ActionResponse assignDevice(
        @RequestHeader(API_KEY) String key,
        @PathVariable int locationId,
        @PathVariable String deviceUuid)
    {
        return deviceAction.assignDevice(contextFactory.current(), locationId, deviceUuid);
    }

    @RequestMapping(method = RequestMethod.DELETE, path = "{locationId}/devices/{deviceUuid}")
    public ActionResponse cancelDeviceAssignment(
        @RequestHeader(API_KEY) String key,
        @PathVariable int locationId,
        @PathVariable String deviceUuid)
    {
        return deviceAction.cancelAssignment(contextFactory.current(), locationId, deviceUuid);
    }
}
