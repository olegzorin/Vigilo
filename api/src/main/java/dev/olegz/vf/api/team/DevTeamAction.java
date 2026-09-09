package dev.olegz.vf.api.team;

import java.util.List;

import dev.olegz.vf.api.web.support.ActionContext;
import dev.olegz.vf.api.web.support.ActionResponse;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.common.util.CollectionOps;
import dev.olegz.vf.core.domain.lambdaversion.DevTeam;
import dev.olegz.vf.core.service.dev.DevTeamsService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DevTeamAction {
    private static final Logger logger = LoggerFactory.getLogger(DevTeamAction.class);

    private final DevTeamsService devTeamsService;

    public DevTeamAction(DevTeamsService devTeamsService) {
        this.devTeamsService = devTeamsService;
    }

    public Response listDevTeams(ActionContext ctx, Integer userId) {
        logger.debug(">listDevTeams() userId={}", userId);

        Response response = new Response();
        response.teams = CollectionOps.map(devTeamsService.getDevTeams(userId), ApiDevTeamSummary::new);
        response.collectionTotalSize = response.teams == null ? 0 : response.teams.size();

        logger.debug("<listDevTeams() userId={}", userId);
        return response;
    }

    public Response getDevTeam(ActionContext ctx, int devTeamId) {
        logger.debug(">getDevTeam() devTeamId={}", devTeamId);

        DevTeam devTeam = devTeamsService.getDevTeam(devTeamId);
        if (devTeam == null) {
            throw new ObjectNotFoundException("Dev team " + devTeamId + " not found");
        }

        Response response = new Response();
        response.team = new ApiDevTeamDetails(devTeam);

        logger.debug("<getDevTeam() devTeamId={}", devTeamId);
        return response;
    }

    public Response createDevTeam(ActionContext ctx, CreateDevTeamRequest request) {
        DevTeam devTeam = devTeamsService.createDevTeam(ctx.userId(), request.name, request.description);
        Response response = new Response();
        response.team = new ApiDevTeamDetails(devTeam);
        return response;
    }

    public ActionResponse addDevTeamMember(ActionContext ctx, int devTeamId, int userId) {
        devTeamsService.addDevTeamMember(ctx.userId(), devTeamId, userId);
        return new ActionResponse();
    }

    public ActionResponse deleteDevTeamMember(ActionContext ctx, int devTeamId, int userId) {
        devTeamsService.deleteDevTeamMember(ctx.userId(), devTeamId, userId);
        return new ActionResponse();
    }

    public static class CreateDevTeamRequest {
        public @NotBlank(message = "name") @Size(max = 100, message = "name") String name;
        public @Size(max = 1000, message = "description") String description;
    }

    public static class Response extends ActionResponse {
        public List<ApiDevTeamSummary> teams;
        public ApiDevTeamDetails team;
    }
}
