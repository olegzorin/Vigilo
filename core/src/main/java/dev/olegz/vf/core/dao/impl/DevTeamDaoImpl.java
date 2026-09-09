package dev.olegz.vf.core.dao.impl;

import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.exception.DuplicateEntityException;
import dev.olegz.vf.core.dao.DevTeamDao;
import dev.olegz.vf.core.dao.mapper.DevTeamMapper;
import dev.olegz.vf.core.domain.lambdaversion.DevTeam;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository("devTeamsDao")
public class DevTeamDaoImpl implements DevTeamDao {
    private final DevTeamMapper mapper;

    public DevTeamDaoImpl(DevTeamMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<DevTeam> getDevTeams(Integer userId) {
        return mapper.selectDevTeams(userId);
    }

    @Override
    public DevTeam getDevTeam(int devTeamId) {
        return mapper.selectDevTeam(devTeamId);
    }

    @Override
    public boolean checkDevTeamMember(int devTeamId, int userId) {
        return mapper.checkDevTeamMember(devTeamId, userId);
    }

    @Override
    public void insertDevTeam(DevTeam devTeam) {
        try {
            mapper.insertDevTeam(devTeam);
        } catch (DuplicateKeyException e) {
            throw new DuplicateEntityException("Dev team already exists");
        }
    }

    @Override
    public boolean insertDevTeamMember(int devTeamId, int userId) {
        try {
            return mapper.insertDevTeamMember(devTeamId, userId, Datetime.now(), null) == 1;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    public boolean deleteDevTeamMember(int devTeamId, int userId) {
        return mapper.deleteDevTeamMember(devTeamId, userId) == 1;
    }

}
