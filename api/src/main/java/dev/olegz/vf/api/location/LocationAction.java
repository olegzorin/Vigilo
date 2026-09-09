package dev.olegz.vf.api.location;

import java.util.List;

import dev.olegz.vf.api.account.ApiAddress;
import dev.olegz.vf.api.account.ApiUser;
import dev.olegz.vf.api.web.support.ActionContext;
import dev.olegz.vf.api.web.support.ActionResponse;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.common.exception.OperationNotAllowedException;
import dev.olegz.vf.common.util.CollectionOps;
import dev.olegz.vf.registry.dao.DeviceDao;
import dev.olegz.vf.registry.dao.LocationDao;
import dev.olegz.vf.registry.dao.UserLocationDao;
import dev.olegz.vf.registry.domain.account.Location;
import dev.olegz.vf.registry.domain.account.LocationUser;
import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.registry.service.account.UserLocationAssignmentService;
import dev.olegz.vf.registry.service.account.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LocationAction {
    private final LocationDao locationDao;
    private final DeviceDao deviceDao;
    private final UserLocationDao userLocationsDao;
    private final UserService userService;
    private final UserLocationAssignmentService userLocationAssignmentService;

    public LocationAction(
        LocationDao locationDao,
        DeviceDao deviceDao,
        UserLocationDao userLocationsDao,
        UserService userService,
        UserLocationAssignmentService userLocationAssignmentService)
    {
        this.locationDao = locationDao;
        this.deviceDao = deviceDao;
        this.userLocationsDao = userLocationsDao;
        this.userService = userService;
        this.userLocationAssignmentService = userLocationAssignmentService;
    }

    public Response listLocations(ActionContext ctx) {
        User caller = ctx.user();
        ctx.requireAdmin();
        List<Location> locations = locationDao.getLocationsByOrganization(caller.organizationId);

        Response response = new Response();
        response.locations = CollectionOps.map(locations, ApiLocation::new);
        if (response.locations == null) response.locations = List.of();
        response.collectionTotalSize = response.locations.size();
        return response;
    }

    public LocationDetailResponse getLocation(ActionContext ctx, int locationId) {
        Location location = getAdminLocation(ctx, locationId);
        User caller = ctx.user();
        LocationDetailResponse response = new LocationDetailResponse();
        response.location = new ApiLocationDetails(
            location,
            locationDao.getLocationCurrentState(caller.organizationId, locationId));
        response.users = CollectionOps.map(userService.getUsersByLocation(locationId), ApiUser::new);
        if (response.users == null) response.users = List.of();
        response.collectionTotalSize = response.users.size();
        return response;
    }

    public Response createLocation(ActionContext ctx, CreateLocationRequest request) {
        ctx.requireSameOrganization(request.organizationId);
        ctx.requireAdmin();

        Location location = new Location();
        location.locationName = request.locationName;
        location.organizationId = request.organizationId;
        location.address = request.address.toAddress();
        locationDao.insertLocation(location);
        return locationResponse(location);
    }

    public Response updateLocation(ActionContext ctx, int locationId, UpdateLocationRequest request) {
        Location location = getAdminLocation(ctx, locationId);
        location.locationName = request.locationName;
        location.address = request.address.toAddress();
        if (!locationDao.updateLocation(location)) {
            throw locationNotFound(locationId);
        }
        return locationResponse(location);
    }

    @Transactional
    public ActionResponse deleteLocation(ActionContext ctx, int locationId) {
        getAdminLocation(ctx, locationId);
        if (userLocationsDao.hasActiveAssignments(locationId)) {
            throw new OperationNotAllowedException(
                "Cannot delete location " + locationId + " while it has active user assignments");
        }
        if (deviceDao.hasActiveLocationAssignments(locationId)) {
            throw new OperationNotAllowedException(
                "Cannot delete location " + locationId + " while it has active device assignments");
        }
        if (!locationDao.deleteLocation(locationId)) {
            throw locationNotFound(locationId);
        }
        return new ActionResponse();
    }

    public Response assignUser(ActionContext ctx, int locationId, int userId) {
        LocationUser assignment = userLocationAssignmentService.assignUser(ctx.user(), locationId, userId);

        Response response = new Response();
        response.assignment = new ApiUserLocation(assignment);
        return response;
    }

    public ActionResponse cancelAssignment(ActionContext ctx, int locationId, int userId) {
        userLocationAssignmentService.cancelAssignment(ctx.user(), locationId, userId);
        return new ActionResponse();
    }

    private Location getAdminLocation(ActionContext ctx, int locationId) {
        int organizationId = ctx.user().organizationId;
        Location location = getExistingLocation(organizationId, locationId);
        ctx.requireAdmin();
        return location;
    }

    private Location getExistingLocation(int organizationId, int locationId) {
        Location location = locationDao.getOrganizationLocation(organizationId, locationId);
        if (location == null) {
            throw locationNotFound(locationId);
        }
        return location;
    }

    private ObjectNotFoundException locationNotFound(int locationId) {
        return new ObjectNotFoundException("Location " + locationId + " not found");
    }

    private Response locationResponse(Location location) {
        Response response = new Response();
        response.location = new ApiLocation(location);
        return response;
    }

    public static class CreateLocationRequest {
        public @NotNull(message = "locationName") String locationName;
        public @Min(value = 1, message = "organizationId") int organizationId;
        public @Valid @NotNull(message = "address") ApiAddress address;
    }

    public static class UpdateLocationRequest {
        public @NotNull(message = "locationName") String locationName;
        public @Valid @NotNull(message = "address") ApiAddress address;
    }

    public static class Response extends ActionResponse {
        public List<ApiLocation> locations;
        public ApiLocation location;
        public ApiUserLocation assignment;
    }

    public static class LocationDetailResponse extends ActionResponse {
        public ApiLocationDetails location;
        public List<ApiUser> users;
    }
}
