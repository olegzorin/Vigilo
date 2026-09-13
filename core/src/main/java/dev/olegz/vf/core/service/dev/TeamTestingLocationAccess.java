package dev.olegz.vf.core.service.dev;

import dev.olegz.vf.core.dao.DevTeamDao;
import dev.olegz.vf.registry.service.account.TestingLocationAccess;
import org.springframework.stereotype.Service;

@Service
public class TeamTestingLocationAccess implements TestingLocationAccess {
    private final DevTeamDao teams;
    public TeamTestingLocationAccess(DevTeamDao teams) { this.teams = teams; }
    public boolean hasAccess(int userId, int locationId) { return teams.hasTestingLocationAccess(userId, locationId); }
}
