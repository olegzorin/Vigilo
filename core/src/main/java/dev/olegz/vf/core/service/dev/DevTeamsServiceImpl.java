package dev.olegz.vf.core.service.dev;

import java.util.List;

import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.common.exception.DuplicateEntityException;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.common.exception.OperationNotAllowedException;
import dev.olegz.vf.core.dao.DevTeamDao;
import dev.olegz.vf.registry.dao.UserDao;
import dev.olegz.vf.core.domain.lambdaversion.DevTeam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("devTeamService")
public class DevTeamsServiceImpl implements DevTeamsService {

    private final DevTeamDao devTeamDao;
    private final UserDao userDao;

    public DevTeamsServiceImpl(DevTeamDao devTeamDao, UserDao userDao) {
        this.devTeamDao = devTeamDao;
        this.userDao = userDao;
    }

    @Override
    public List<DevTeam> getDevTeams(Integer userId) {
        return devTeamDao.getDevTeams(userId);
    }

    @Override
    public DevTeam getDevTeam(int devTeamId) {
        return devTeamDao.getDevTeam(devTeamId);
    }

    @Override
    @Transactional
    public DevTeam createDevTeam(int ownerUserId, String name, String description) {
        requireUser(ownerUserId);
        DevTeam devTeam = new DevTeam(ownerUserId, name, description);
        devTeamDao.insertDevTeam(devTeam);
        if (!devTeamDao.insertDevTeamMember(devTeam.devTeamId, ownerUserId)) {
            throw new IllegalStateException("Could not add owner to dev team " + devTeam.devTeamId);
        }
        return devTeamDao.getDevTeam(devTeam.devTeamId);
    }

    @Override
    @Transactional
    public void addDevTeamMember(int callerUserId, int devTeamId, int userId) {
        requireOwner(callerUserId, devTeamId);
        requireUser(userId);
        if (!devTeamDao.insertDevTeamMember(devTeamId, userId)) {
            throw new DuplicateEntityException("User " + userId + " is already a member of dev team " + devTeamId);
        }
    }

    @Override
    @Transactional
    public void deleteDevTeamMember(int callerUserId, int devTeamId, int userId) {
        DevTeam devTeam = requireOwner(callerUserId, devTeamId);
        if (devTeam.ownerUserId == userId) {
            throw new OperationNotAllowedException("The owner cannot be removed from dev team " + devTeamId);
        }
        if (!devTeamDao.deleteDevTeamMember(devTeamId, userId)) {
            throw new ObjectNotFoundException("User " + userId + " is not a member of dev team " + devTeamId);
        }
    }

    private DevTeam requireOwner(int callerUserId, int devTeamId) {
        DevTeam devTeam = devTeamDao.getDevTeam(devTeamId);
        if (devTeam == null) {
            throw new ObjectNotFoundException("Dev team " + devTeamId + " not found");
        }
        if (devTeam.ownerUserId != callerUserId) {
            throw new AccessDeniedException("Only the owner can modify dev team " + devTeamId + " members");
        }
        return devTeam;
    }

    private void requireUser(int userId) {
        if (userDao.getUser(userId) == null) {
            throw new ObjectNotFoundException("User " + userId + " not found");
        }
    }

}
