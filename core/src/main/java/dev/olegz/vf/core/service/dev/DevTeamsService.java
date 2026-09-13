package dev.olegz.vf.core.service.dev;

import java.util.List;
import dev.olegz.vf.core.domain.lambdaversion.DevTeam;
import dev.olegz.vf.registry.domain.account.User;

public interface DevTeamsService {
    List<DevTeam> getDevTeams(User caller, Integer userId);
    List<DevTeam> getTeamsByTestingLocation(User caller, int locationId);
    DevTeam getDevTeam(User caller, int devTeamId);
    DevTeam createDevTeam(User caller, int ownerUserId, String name, String description);
    void addDevTeamMember(User caller, int devTeamId, int userId);
    void deleteDevTeamMember(User caller, int devTeamId, int userId);
    void setOwner(User caller, int devTeamId, int userId);
    void grantTestingLocation(User caller, int devTeamId, int locationId);
    void revokeTestingLocation(User caller, int devTeamId, int locationId);
}
