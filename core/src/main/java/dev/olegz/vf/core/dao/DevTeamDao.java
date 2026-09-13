package dev.olegz.vf.core.dao;

import org.apache.ibatis.annotations.Param;
import java.util.List;

import dev.olegz.vf.core.domain.lambdaversion.DevTeam;

public interface DevTeamDao {

    List<DevTeam> getDevTeams(Integer userId);

    DevTeam getDevTeam(int devTeamId);

    List<DevTeam> getTeamsByTestingLocation(int locationId);

    boolean checkDevTeamMember(int devTeamId, int userId);

    void insertDevTeam(DevTeam devTeam);

    boolean insertDevTeamMember(int devTeamId, int userId);

    boolean deleteDevTeamMember(int devTeamId, int userId);

    boolean hasTestingLocationAccess(@Param("userId") int userId, @Param("locationId") int locationId);
    void grantTestingLocation(@Param("devTeamId") int devTeamId, @Param("locationId") int locationId);
    void revokeTestingLocation(@Param("devTeamId") int devTeamId, @Param("locationId") int locationId);
    void lockTeam(int devTeamId);
    void updateOwner(@Param("devTeamId") int devTeamId, @Param("userId") int userId);
}
