package dev.olegz.vf.api.lambda.management;

import java.util.List;

import dev.olegz.vf.api.web.support.ActionContextFactory;
import dev.olegz.vf.api.web.support.ActionResponse;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersionStatus;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import static dev.olegz.vf.api.web.ApiHeaders.API_KEY;

@RestController("lambdaConfigController")
@RequestMapping(path = "/vf/lambdas", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
public class LambdaManagementController {

    private final LambdaManagementAction lambdaManagementAction;
    private final ActionContextFactory contextFactory;

    public LambdaManagementController(
        LambdaManagementAction lambdaManagementAction,
        ActionContextFactory contextFactory)
    {
        this.lambdaManagementAction = lambdaManagementAction;
        this.contextFactory = contextFactory;
    }

    @RequestMapping(method = RequestMethod.GET)
    public ActionResponse getLambdas(
        @RequestHeader(API_KEY) String key,
        @RequestParam(required = false) Integer devTeamId)
    {
        return lambdaManagementAction.getLambdas(contextFactory.current(), devTeamId);
    }

    @RequestMapping(method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ActionResponse postLambda(
        @RequestHeader(API_KEY) String key,
        @Valid @RequestBody LambdaManagementAction.LambdaCreateRequest request)
    {
        return lambdaManagementAction.postLambda(contextFactory.current(), request);
    }

    @RequestMapping(method = RequestMethod.PUT, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ActionResponse putLambda(
        @RequestHeader(API_KEY) String key,
        @Valid @RequestBody LambdaManagementAction.LambdaUpdateRequest request)
    {
        return lambdaManagementAction.putLambda(contextFactory.current(), request);
    }

    /***********************************************************
     *                                                         *
     *                      Lambda Versions                       *
     *                                                         *
     ***********************************************************/

    @RequestMapping(method = RequestMethod.PUT, path = "versions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ActionResponse putLambdaVersion(
        @RequestHeader(API_KEY) String key,
        @RequestParam int lambdaId,
        @Valid @RequestBody LambdaManagementAction.PutVersionRequest request)
    {
        return lambdaManagementAction.putLambdaConfiguration(contextFactory.current(), lambdaId, request);
    }

    @RequestMapping(method = RequestMethod.GET, path = "versions")
    public ActionResponse getLambdaVersions(
        @RequestHeader(API_KEY) String key,
        @RequestParam int lambdaId,
        @RequestParam(name = "status", required = false) List<LambdaVersionStatus> statuses,
        @RequestParam(required = false) String version)
    {
        return lambdaManagementAction.getLambdaVersions(contextFactory.current(), lambdaId, statuses, version);
    }

    @RequestMapping(method = RequestMethod.PUT, path = "versions/promote")
    public ActionResponse promoteTestVersionToProduction(
        @RequestHeader(API_KEY) String key,
        @RequestParam int lambdaId)
    {
        return lambdaManagementAction.promoteTestVersionToProduction(contextFactory.current(), lambdaId);
    }

    @RequestMapping(method = RequestMethod.PUT, path = "versions/discard")
    public ActionResponse discardTestVersion(
        @RequestHeader(API_KEY) String key,
        @RequestParam int lambdaId)
    {
        return lambdaManagementAction.discardTestVersion(contextFactory.current(), lambdaId);
    }

    @RequestMapping(method = RequestMethod.PUT, path = "versions/rollback")
    public ActionResponse rollbackProductionVersion(
        @RequestHeader(API_KEY) String key,
        @RequestParam int lambdaId)
    {
        return lambdaManagementAction.rollbackProductionVersion(contextFactory.current(), lambdaId);
    }

    @RequestMapping(method = RequestMethod.DELETE, path = "versions")
    public ActionResponse deleteActiveLambdaVersions(
        @RequestHeader(API_KEY) String key,
        @RequestParam int lambdaId)
    {
        return lambdaManagementAction.deleteActiveLambdaVersions(contextFactory.current(), lambdaId);
    }

}
