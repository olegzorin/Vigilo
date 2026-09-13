package dev.olegz.vf.api.team;

import dev.olegz.vf.core.domain.lambdaversion.DevTeam;

/** A team granted access to a testing location. */
public record ApiGrantedTeam(int devTeamId, String name) {
    public ApiGrantedTeam(DevTeam team) {
        this(team.devTeamId, team.name);
    }
}
