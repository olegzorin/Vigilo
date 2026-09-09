package dev.olegz.vf.core.service.dev;

import java.util.List;

import dev.olegz.vf.core.domain.lambdaversion.DevTeam;

public interface DevTeamsService {

    List<DevTeam> getDevTeams(Integer userId);

    DevTeam getDevTeam(int devTeamId);

    DevTeam createDevTeam(int ownerUserId, String name, String description);

    void addDevTeamMember(int callerUserId, int devTeamId, int userId);

    void deleteDevTeamMember(int callerUserId, int devTeamId, int userId);
}
