package dev.olegz.vf.api.location;

import dev.olegz.vf.core.service.dev.DevTeamsService;
import dev.olegz.vf.api.team.ApiGrantedTeam;

import dev.olegz.vf.registry.domain.account.LocationType;
import dev.olegz.vf.registry.service.account.AccessService;
import java.util.List;

import dev.olegz.vf.api.account.ApiAddress;
import dev.olegz.vf.registry.domain.account.Resident;
import dev.olegz.vf.api.web.support.ActionContext;
import dev.olegz.vf.api.web.support.ActionResponse;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.common.exception.OperationNotAllowedException;
import dev.olegz.vf.common.util.CollectionOps;
import dev.olegz.vf.registry.dao.DeviceDao;
import dev.olegz.vf.registry.dao.LocationDao;
import dev.olegz.vf.registry.dao.ResidentLocationDao;
import dev.olegz.vf.registry.domain.account.Location;
import dev.olegz.vf.registry.domain.account.LocationResident;
import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.registry.service.account.ResidentLocationAssignmentService;
import dev.olegz.vf.registry.dao.ResidentDao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LocationAction {
    private final DevTeamsService devTeams;
    private final AccessService access;
    private final LocationDao locationDao;
    private final DeviceDao deviceDao;
    private final ResidentLocationDao residentLocationsDao;
    private final ResidentDao residentDao;
    private final ResidentLocationAssignmentService residentLocationAssignmentService;

    public LocationAction(
        DevTeamsService devTeams,
        AccessService access,
        LocationDao locationDao,
        DeviceDao deviceDao,
        ResidentLocationDao residentLocationsDao,
        ResidentDao residentDao,
        ResidentLocationAssignmentService residentLocationAssignmentService)
    {
        this.devTeams = devTeams;
        this.access = access;
        this.locationDao = locationDao;
        this.deviceDao = deviceDao;
        this.residentLocationsDao = residentLocationsDao;
        this.residentDao = residentDao;
        this.residentLocationAssignmentService = residentLocationAssignmentService;
    }

    public Response listLocations(ActionContext ctx) {
        User caller = ctx.user();
        List<Location> locations = locationDao.getLocationsByOrganization(caller.organizationId)
            .stream().filter(location -> access.canAccess(caller, location)).toList();

        Response response = new Response();
        response.locations = CollectionOps.map(locations, ApiLocation::new);
        if (response.locations == null) response.locations = List.of();
        response.collectionTotalSize = response.locations.size();
        return response;
    }

    public LocationDetailResponse getLocation(ActionContext ctx, int locationId) {
        Location location = access.requireLocation(ctx.user(), locationId);
        User caller = ctx.user();
        LocationDetailResponse response = new LocationDetailResponse();
        response.location = new ApiLocationDetails(
            location,
            locationDao.getLocationCurrentState(caller.organizationId, locationId));
        response.location.grantedTeams = CollectionOps.map(
            devTeams.getTeamsByTestingLocation(caller, locationId), ApiGrantedTeam::new);
        response.residents = residentDao.getResidentsByLocation(locationId);
        if (response.residents == null) response.residents = List.of();
        response.collectionTotalSize = response.residents.size();
        return response;
    }

    public Response createLocation(ActionContext ctx, CreateLocationRequest request) {
        ctx.requireSameOrganization(request.organizationId);
        ctx.requireAdmin();

        Location location = new Location();
        location.locationType = request.locationType;
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
        if (residentLocationsDao.hasActiveAssignments(locationId)) {
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

    public Response assignResident(ActionContext ctx, int locationId, int residentId) {
        LocationResident assignment = residentLocationAssignmentService.assignResident(ctx.user(), locationId, residentId);

        Response response = new Response();
        response.assignment = new ApiResidentLocation(assignment);
        return response;
    }

    public ActionResponse cancelAssignment(ActionContext ctx, int locationId, int residentId) {
        residentLocationAssignmentService.cancelAssignment(ctx.user(), locationId, residentId);
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
        public @NotNull LocationType locationType = LocationType.OPERATIONAL;
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
        public ApiResidentLocation assignment;
    }

    public static class LocationDetailResponse extends ActionResponse {
        public ApiLocationDetails location;
        public List<Resident> residents;
    }
}
