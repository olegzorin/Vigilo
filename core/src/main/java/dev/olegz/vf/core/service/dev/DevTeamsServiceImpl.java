package dev.olegz.vf.core.service.dev;

import java.util.List;
import dev.olegz.vf.common.exception.*;
import dev.olegz.vf.core.dao.DevTeamDao;
import dev.olegz.vf.core.domain.lambdaversion.DevTeam;
import dev.olegz.vf.registry.dao.LocationDao;
import dev.olegz.vf.registry.dao.UserDao;
import dev.olegz.vf.registry.domain.account.*;
import dev.olegz.vf.registry.service.account.AccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DevTeamsServiceImpl implements DevTeamsService {
    private final DevTeamDao teams;
    private final UserDao users;
    private final LocationDao locations;
    private final AccessService access;
    public DevTeamsServiceImpl(DevTeamDao teams, UserDao users, LocationDao locations, AccessService access) {
        this.teams = teams;
        this.users = users;
        this.locations = locations;
        this.access = access;
    }
    public List<DevTeam> getDevTeams(User caller, Integer userId) {
        boolean admin = access.isAdmin(caller);
        if (!admin && userId != null && userId != caller.userId) throw new AccessDeniedException("Cannot list another developer's teams");
        return teams.getDevTeams(admin ? userId : caller.userId).stream()
            .filter(team -> team.organizationId == caller.organizationId).toList();
    }
    public List<DevTeam> getTeamsByTestingLocation(User caller, int locationId) {
        Location location = access.requireLocation(caller, locationId);
        if (location.locationType != LocationType.TESTING) return List.of();
        boolean admin = access.isAdmin(caller);
        return teams.getTeamsByTestingLocation(locationId).stream()
            .filter(team -> team.organizationId == caller.organizationId)
            .filter(team -> admin || teams.checkDevTeamMember(team.devTeamId, caller.userId))
            .toList();
    }
    public DevTeam getDevTeam(User caller, int devTeamId) {
        DevTeam team = requireTeam(caller, devTeamId);
        if (!access.isAdmin(caller) && !teams.checkDevTeamMember(devTeamId, caller.userId)) throw new AccessDeniedException("Team is not accessible");
        return team;
    }
    @Transactional
    public DevTeam createDevTeam(User caller, int ownerUserId, String name, String description) {
        access.requireAdmin(caller);
        requireDeveloper(caller.organizationId, ownerUserId);
        Location location = new Location();
        location.organizationId = caller.organizationId;
        location.locationName = name + " testing";
        location.locationType = LocationType.TESTING;
        location.address = new Address();
        location.address.timezone = "UTC";
        locations.insertLocation(location);
        DevTeam team = new DevTeam(ownerUserId, name, description);
        team.organizationId = caller.organizationId;
        team.testingLocationId = location.locationId;
        teams.insertDevTeam(team);
        if (!teams.insertDevTeamMember(team.devTeamId, ownerUserId)) throw new IllegalStateException("Could not add team owner");
        teams.grantTestingLocation(team.devTeamId, location.locationId);
        return teams.getDevTeam(team.devTeamId);
    }
    @Transactional
    public void addDevTeamMember(User caller, int devTeamId, int userId) {
        DevTeam team = requireAdminTeam(caller, devTeamId);
        requireDeveloper(team.organizationId, userId);
        if (!teams.insertDevTeamMember(devTeamId, userId)) throw new DuplicateEntityException("Developer is already a team member");
    }
    @Transactional
    public void deleteDevTeamMember(User caller, int devTeamId, int userId) {
        DevTeam team = requireAdminTeam(caller, devTeamId);
        if (team.ownerUserId == userId) throw new OperationNotAllowedException("Transfer ownership before removing the owner");
        if (!teams.deleteDevTeamMember(devTeamId, userId)) throw new ObjectNotFoundException("Team member not found");
    }
    @Transactional
    public void setOwner(User caller, int devTeamId, int userId) {
        DevTeam team = requireAdminTeam(caller, devTeamId);
        requireDeveloper(team.organizationId, userId);
        if (!teams.checkDevTeamMember(devTeamId, userId)) throw new OperationNotAllowedException("Owner must be an active team member");
        teams.updateOwner(devTeamId, userId);
    }
    @Transactional
    public void grantTestingLocation(User caller, int devTeamId, int locationId) {
        requireAdminTeam(caller, devTeamId);
        Location location = access.requireLocation(caller, locationId);
        if (location.locationType != LocationType.TESTING) throw new AccessDeniedException("Teams may only receive testing locations");
        teams.grantTestingLocation(devTeamId, locationId);
    }
    @Transactional
    public void revokeTestingLocation(User caller, int devTeamId, int locationId) {
        requireAdminTeam(caller, devTeamId);
        teams.revokeTestingLocation(devTeamId, locationId);
    }
    private DevTeam requireAdminTeam(User caller, int id) {
        access.requireAdmin(caller);
        teams.lockTeam(id);
        return requireTeam(caller, id);
    }
    private DevTeam requireTeam(User caller, int id) {
        DevTeam team = teams.getDevTeam(id);
        if (team == null || team.organizationId != caller.organizationId) throw new ObjectNotFoundException("Team not found");
        return team;
    }
    private void requireDeveloper(int organizationId, int id) {
        User user = users.getUser(id);
        if (user == null || user.organizationId != organizationId || user.accountType != AccountType.DEVELOPER) throw new AccessDeniedException("Owner and members must be developer accounts in the team's organization");
    }
}
