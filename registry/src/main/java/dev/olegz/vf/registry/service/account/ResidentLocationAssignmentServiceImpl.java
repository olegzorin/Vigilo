package dev.olegz.vf.registry.service.account;

import java.util.List;

import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.common.exception.DuplicateEntityException;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.registry.dao.LocationDao;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.dao.ResidentLocationDao;
import dev.olegz.vf.registry.domain.account.Location;
import dev.olegz.vf.registry.domain.account.LocationResident;
import dev.olegz.vf.registry.domain.account.Organization;
import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.registry.domain.account.Resident;
import dev.olegz.vf.registry.domain.account.LocationType;
import dev.olegz.vf.registry.dao.ResidentDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("residentLocationAssignmentService")
public class ResidentLocationAssignmentServiceImpl implements ResidentLocationAssignmentService {
    private final LocationDao locationDao;
    private final OrganizationDao organizationDao;
    private final ResidentLocationDao residentLocationDao;
    private final ResidentDao residentDao;
    private final List<LocationAssignmentChangeHandler> changeHandlers;

    public ResidentLocationAssignmentServiceImpl(
        LocationDao locationDao,
        OrganizationDao organizationDao,
        ResidentLocationDao residentLocationDao,
        ResidentDao residentDao,
        List<LocationAssignmentChangeHandler> changeHandlers)
    {
        this.locationDao = locationDao;
        this.organizationDao = organizationDao;
        this.residentLocationDao = residentLocationDao;
        this.residentDao = residentDao;
        this.changeHandlers = List.copyOf(changeHandlers);
    }

    @Override
    @Transactional
    public LocationResident assignResident(User caller, int locationId, int residentId) {
        Location location = getAdminLocation(caller, locationId);
        requireResidentInOrganization(residentId, location);

        LocationResident assignment = new LocationResident();
        assignment.residentId = residentId;
        assignment.locationId = locationId;
        if (!residentLocationDao.insertResidentLocation(assignment)) {
            throw new DuplicateEntityException("Resident " + residentId + " already has an active location assignment");
        }
        notifyLocationMembershipChanged(locationId);
        return assignment;
    }

    @Override
    @Transactional
    public void cancelAssignment(User caller, int locationId, int residentId) {
        Location location = getAdminLocation(caller, locationId);
        requireResidentInOrganization(residentId, location);
        if (!residentLocationDao.deleteResidentLocation(residentId, locationId)) {
            throw new ObjectNotFoundException(
                "Active assignment of resident " + residentId + " to location " + locationId + " not found");
        }
        notifyLocationMembershipChanged(locationId);
    }

    private void notifyLocationMembershipChanged(int locationId) {
        changeHandlers.forEach(handler -> handler.locationMembershipChanged(locationId));
    }

    private Location getAdminLocation(User caller, int locationId) {
        Location location = locationDao.getOrganizationLocation(caller.organizationId, locationId);
        if (location == null) {
            throw new ObjectNotFoundException("Location " + locationId + " not found");
        }

        Organization organization = organizationDao.getOrganization(caller.organizationId);
        if (organization == null || organization.adminUserId == null || organization.adminUserId != caller.userId) {
            throw new AccessDeniedException("Administrator privileges required");
        }
        return location;
    }

    private void requireResidentInOrganization(int residentId, Location location) {
        Resident resident = residentDao.getResident(location.organizationId, residentId);
        if (resident == null) {
            throw new ObjectNotFoundException("Resident " + residentId + " not found");
        }
        if (resident.synthetic != (location.locationType == LocationType.TESTING)) {
            throw new AccessDeniedException("Synthetic residents require testing locations; real residents require operational locations");
        }
        if (resident.organizationId != location.organizationId) {
            throw new AccessDeniedException("Access to resident " + residentId + " denied");
        }
    }
}
