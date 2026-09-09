package dev.olegz.vf.registry.service.account;

import java.util.List;

import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.common.exception.DuplicateEntityException;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.registry.dao.LocationDao;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.dao.UserLocationDao;
import dev.olegz.vf.registry.domain.account.Location;
import dev.olegz.vf.registry.domain.account.LocationUser;
import dev.olegz.vf.registry.domain.account.Organization;
import dev.olegz.vf.registry.domain.account.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("userLocationAssignmentService")
public class UserLocationAssignmentServiceImpl implements UserLocationAssignmentService {
    private final LocationDao locationDao;
    private final OrganizationDao organizationDao;
    private final UserLocationDao userLocationDao;
    private final UserService userService;
    private final List<LocationAssignmentChangeHandler> changeHandlers;

    public UserLocationAssignmentServiceImpl(
        LocationDao locationDao,
        OrganizationDao organizationDao,
        UserLocationDao userLocationDao,
        UserService userService,
        List<LocationAssignmentChangeHandler> changeHandlers)
    {
        this.locationDao = locationDao;
        this.organizationDao = organizationDao;
        this.userLocationDao = userLocationDao;
        this.userService = userService;
        this.changeHandlers = List.copyOf(changeHandlers);
    }

    @Override
    @Transactional
    public LocationUser assignUser(User caller, int locationId, int userId) {
        Location location = getAdminLocation(caller, locationId);
        requireUserInOrganization(userId, location.organizationId);

        LocationUser assignment = new LocationUser();
        assignment.userId = userId;
        assignment.locationId = locationId;
        if (!userLocationDao.insertUserLocation(assignment)) {
            throw new DuplicateEntityException("User " + userId + " already has an active location assignment");
        }
        notifyLocationMembershipChanged(locationId);
        return assignment;
    }

    @Override
    @Transactional
    public void cancelAssignment(User caller, int locationId, int userId) {
        Location location = getAdminLocation(caller, locationId);
        requireUserInOrganization(userId, location.organizationId);
        if (!userLocationDao.deleteUserLocation(userId, locationId)) {
            throw new ObjectNotFoundException(
                "Active assignment of user " + userId + " to location " + locationId + " not found");
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

    private void requireUserInOrganization(int userId, int organizationId) {
        User user = userService.getUser(userId);
        if (user == null) {
            throw new ObjectNotFoundException("User " + userId + " not found");
        }
        if (user.organizationId != organizationId) {
            throw new AccessDeniedException("Access to user " + userId + " denied");
        }
    }
}
