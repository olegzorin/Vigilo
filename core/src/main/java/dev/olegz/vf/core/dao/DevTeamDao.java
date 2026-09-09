package dev.olegz.vf.core.dao;

import java.util.List;

import dev.olegz.vf.core.domain.lambdaversion.DevTeam;

public interface DevTeamDao {

    List<DevTeam> getDevTeams(Integer userId);

    DevTeam getDevTeam(int devTeamId);

    boolean checkDevTeamMember(int devTeamId, int userId);

    void insertDevTeam(DevTeam devTeam);

    boolean insertDevTeamMember(int devTeamId, int userId);

    boolean deleteDevTeamMember(int devTeamId, int userId);

}
