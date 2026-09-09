package dev.olegz.vf.core.dao.mapper;

import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.core.domain.lambdaversion.DevTeam;
import org.apache.ibatis.annotations.Param;

public interface DevTeamMapper {

    List<DevTeam> selectDevTeams(Integer userId);

    DevTeam selectDevTeam(int devTeamId);

    boolean checkDevTeamMember(
        @Param("devTeamId") int devTeamId,
        @Param("userId") int userId);

    // For tests only
    void insertDevTeam(DevTeam devTeam);

    // For tests only
    int insertDevTeamMember(
        @Param("devTeamId") int devTeamId,
        @Param("userId") int userId,
        @Param("startDate") Datetime startDate,
        @Param("endDate") Datetime endDate);

    int deleteDevTeamMember(
        @Param("devTeamId") int devTeamId,
        @Param("userId") int userId);

}
