package dev.olegz.vf.api.team;

import dev.olegz.vf.api.web.support.ActionContextFactory;
import dev.olegz.vf.api.web.support.ActionResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import static dev.olegz.vf.api.web.ApiHeaders.API_KEY;

@RestController("devTeamController")
@RequestMapping(path = "/vf/teams", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
public class DevTeamController {

    private final DevTeamAction devTeamAction;
    private final ActionContextFactory contextFactory;

    public DevTeamController(DevTeamAction devTeamAction, ActionContextFactory contextFactory) {
        this.devTeamAction = devTeamAction;
        this.contextFactory = contextFactory;
    }

    @RequestMapping(method = RequestMethod.GET)
    public ActionResponse listDevTeams(
        @RequestHeader(API_KEY) String key,
        @RequestParam(required = false) Integer userId)
    {
        return devTeamAction.listDevTeams(contextFactory.current(), userId);
    }

    @RequestMapping(method = RequestMethod.GET, path = "{devTeamId}")
    public ActionResponse getDevTeam(
        @RequestHeader(API_KEY) String key,
        @PathVariable int devTeamId)
    {
        return devTeamAction.getDevTeam(contextFactory.current(), devTeamId);
    }

    @RequestMapping(method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ActionResponse createDevTeam(
        @RequestHeader(API_KEY) String key,
        @Valid @RequestBody DevTeamAction.CreateDevTeamRequest request)
    {
        return devTeamAction.createDevTeam(contextFactory.current(), request);
    }

    @RequestMapping(method = RequestMethod.POST, path = "{devTeamId}/users/{userId}")
    public ActionResponse addDevTeamMember(
        @RequestHeader(API_KEY) String key,
        @PathVariable int devTeamId,
        @PathVariable int userId)
    {
        return devTeamAction.addDevTeamMember(contextFactory.current(), devTeamId, userId);
    }

    @RequestMapping(method = RequestMethod.DELETE, path = "{devTeamId}/users/{userId}")
    public ActionResponse deleteDevTeamMember(
        @RequestHeader(API_KEY) String key,
        @PathVariable int devTeamId,
        @PathVariable int userId)
    {
        return devTeamAction.deleteDevTeamMember(contextFactory.current(), devTeamId, userId);
    }
}
