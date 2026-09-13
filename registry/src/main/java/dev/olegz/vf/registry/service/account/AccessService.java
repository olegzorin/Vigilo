package dev.olegz.vf.registry.service.account;

import java.util.List;
import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.registry.dao.LocationDao;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.domain.account.*;
import org.springframework.stereotype.Service;

@Service
public class AccessService {
    private final OrganizationDao organizations;
    private final LocationDao locations;
    private final List<TestingLocationAccess> grants;
    public AccessService(OrganizationDao organizations, LocationDao locations, List<TestingLocationAccess> grants) {
        this.organizations = organizations;
        this.locations = locations;
        this.grants = List.copyOf(grants);
    }
    public boolean isAdmin(User user) {
        if (user == null || user.deletedAt != null) return false;
        Organization org = organizations.getOrganization(user.organizationId);
        return org != null && org.adminUserId != null && org.adminUserId == user.userId;
    }
    public void requireAdmin(User user) {
        if (!isAdmin(user)) throw new AccessDeniedException("Organization administrator required");
    }
    public boolean canAccess(User user, Location location) {
        return user != null && user.deletedAt == null && location != null && location.deletedAt == null && user.organizationId == location.organizationId
            && (isAdmin(user) || (user.accountType == AccountType.DEVELOPER
                && location.locationType == LocationType.TESTING
                && grants.stream().anyMatch(grant -> grant.hasAccess(user.userId, location.locationId))));
    }
    public Location requireLocation(User user, int locationId) {
        if (user == null) throw new AccessDeniedException("Authentication required");
        Location location = locations.getOrganizationLocation(user.organizationId, locationId);
        if (location == null) throw new ObjectNotFoundException("Location " + locationId + " not found");
        if (!canAccess(user, location)) throw new AccessDeniedException("Location is not accessible");
        return location;
    }
}
