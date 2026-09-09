package dev.olegz.vf.api.device;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.olegz.vf.api.web.support.ActionContext;
import dev.olegz.vf.api.web.support.ActionResponse;
import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.registry.dao.DeviceDao;
import dev.olegz.vf.registry.dao.LocationDao;
import dev.olegz.vf.registry.domain.account.Location;
import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.registry.domain.device.Device;
import dev.olegz.vf.registry.domain.device.DeviceCurrentState;
import dev.olegz.vf.registry.domain.device.LocationDevice;
import dev.olegz.vf.registry.service.device.DeviceLocationAssignmentService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Component;

@Component
public class DeviceAction {
    private final DeviceDao deviceDao;
    private final LocationDao locationDao;
    private final DeviceLocationAssignmentService assignmentService;

    public DeviceAction(
        DeviceDao deviceDao,
        LocationDao locationDao,
        DeviceLocationAssignmentService assignmentService)
    {
        this.deviceDao = deviceDao;
        this.locationDao = locationDao;
        this.assignmentService = assignmentService;
    }

    public Response listDevices(ActionContext ctx, Integer typeId, Integer locationId) {
        User caller = ctx.user();
        Integer effectiveLocationId = locationId;
        if (ctx.isAdmin()) {
            if (locationId != null && locationDao.getOrganizationLocation(caller.organizationId, locationId) == null) {
                throw new ObjectNotFoundException("Location " + locationId + " not found");
            }
        } else {
            Location currentLocation = locationDao.getLocationByUser(caller);
            if (currentLocation == null) {
                return devicesResponse(List.of(), List.of());
            }
            if (locationId != null && locationId != currentLocation.locationId) {
                throw new AccessDeniedException("Access to location " + locationId + " denied");
            }
            effectiveLocationId = currentLocation.locationId;
        }

        List<Device> devices = deviceDao.getDevices(caller.organizationId, typeId, effectiveLocationId);
        List<LocationDevice> assignments =
            deviceDao.getLocationDevices(caller.organizationId, typeId, effectiveLocationId);
        return devicesResponse(devices, assignments);
    }

    public DeviceDetailResponse getDevice(ActionContext ctx, String deviceUuid) {
        User caller = ctx.user();
        Device device;
        LocationDevice assignment;
        if (ctx.isAdmin()) {
            device = requireDevice(caller.organizationId, deviceUuid);
            assignment = deviceDao.getLocationDevice(caller.organizationId, deviceUuid, null);
        } else {
            Location currentLocation = locationDao.getLocationByUser(caller);
            if (currentLocation == null) {
                throw deviceAccessDenied(deviceUuid);
            }
            assignment =
                deviceDao.getLocationDevice(caller.organizationId, deviceUuid, currentLocation.locationId);
            if (assignment == null) {
                throw deviceAccessDenied(deviceUuid);
            }
            device = requireDevice(caller.organizationId, deviceUuid);
        }

        DeviceCurrentState currentState =
            deviceDao.getDeviceCurrentState(caller.organizationId, deviceUuid);
        DeviceDetailResponse response = new DeviceDetailResponse();
        response.device = new ApiDeviceDetails(device, assignment, currentState);
        return response;
    }

    public Response createDevice(ActionContext ctx, CreateDeviceRequest request) {
        ctx.requireAdmin();
        ctx.requireSameOrganization(request.organizationId);

        Device device = new Device();
        apply(device, request);
        device.organizationId = request.organizationId;
        deviceDao.insertDevice(device);
        return deviceResponse(device, null);
    }

    public Response updateDevice(ActionContext ctx, String deviceUuid, UpdateDeviceRequest request) {
        User caller = ctx.user();
        ctx.requireAdmin();
        Device device = requireDevice(caller.organizationId, deviceUuid);
        apply(device, request);
        if (!deviceDao.updateDevice(device)) {
            throw deviceNotFound(deviceUuid);
        }
        LocationDevice deviceAssignment =
            deviceDao.getLocationDevice(caller.organizationId, deviceUuid, null);
        return deviceResponse(device, deviceAssignment);
    }

    public Response assignDevice(ActionContext ctx, int locationId, String deviceUuid) {
        LocationDevice assignment = assignmentService.assignDevice(ctx.user(), locationId, deviceUuid);
        Response response = new Response();
        response.assignment = new ApiDeviceLocationAssignment(assignment);
        return response;
    }

    public ActionResponse cancelAssignment(ActionContext ctx, int locationId, String deviceUuid) {
        assignmentService.cancelAssignment(ctx.user(), locationId, deviceUuid);
        return new ActionResponse();
    }

    private Response devicesResponse(List<Device> devices, List<LocationDevice> assignments) {
        Map<String, LocationDevice> assignmentsByDevice = new HashMap<>();
        for (LocationDevice assignment : assignments) {
            assignmentsByDevice.put(assignment.deviceUuid, assignment);
        }

        Response response = new Response();
        response.devices = new ArrayList<>(devices.size());
        for (Device device : devices) {
            response.devices.add(new ApiDevice(device, assignmentsByDevice.get(device.deviceUuid)));
        }
        response.collectionTotalSize = response.devices.size();
        return response;
    }

    private Response deviceResponse(Device device, LocationDevice assignment) {
        Response response = new Response();
        response.device = new ApiDevice(device, assignment);
        return response;
    }

    private Device requireDevice(int organizationId, String deviceUuid) {
        Device device = deviceDao.getDevice(organizationId, deviceUuid);
        if (device == null) throw deviceNotFound(deviceUuid);
        return device;
    }

    private static void apply(Device device, DeviceRequest request) {
        if (request instanceof CreateDeviceRequest createRequest) {
            device.deviceUuid = createRequest.deviceUuid;
        }
        device.typeId = request.typeId;
        device.deviceName = request.deviceName;
        device.serialNumber = request.serialNumber;
        device.model = request.model;
        device.vendor = request.vendor;
        device.manufacturer = request.manufacturer;
    }

    private AccessDeniedException deviceAccessDenied(String deviceUuid) {
        return new AccessDeniedException("Access to device " + deviceUuid + " denied");
    }

    private ObjectNotFoundException deviceNotFound(String deviceUuid) {
        return new ObjectNotFoundException("Device " + deviceUuid + " not found");
    }

    public static class DeviceRequest {
        public @Min(value = 1, message = "typeId") int typeId;
        public String deviceName;
        public String serialNumber;
        public String model;
        public String vendor;
        public String manufacturer;
    }

    public static class CreateDeviceRequest extends DeviceRequest {
        public @NotBlank(message = "deviceUuid") String deviceUuid;
        public @Min(value = 1, message = "organizationId") int organizationId;
    }

    public static class UpdateDeviceRequest extends DeviceRequest {
    }

    public static class Response extends ActionResponse {
        public ApiDevice device;
        public List<ApiDevice> devices;
        public ApiDeviceLocationAssignment assignment;
    }

    public static class DeviceDetailResponse extends ActionResponse {
        public ApiDeviceDetails device;
    }
}
