package dev.olegz.vf.core.domain.lambdaversion;

import java.util.List;

/**
 * @author Oleg Zorin on 10/21/21
 */
public class DevTeam {
    public int devTeamId;
    public int ownerUserId;
    public String name;
    public String description;
    public List<DevTeamMember> members;
    public List<Lambda> lambdas;
    public int membersCount;
    public int lambdasCount;

    private DevTeam() {
    }

    public DevTeam(int ownerUserId, String name, String description) {
        this.ownerUserId = ownerUserId;
        this.name = name;
        this.description = description;
    }

    @Override
    public String toString() {
        return "devTeamId=" + devTeamId + ", ownerUserId=" + ownerUserId +
            ", name=" + name + ", members=" + members +
            (description != null ? ", description=" + description : "");
    }
}
