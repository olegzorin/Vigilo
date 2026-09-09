package dev.olegz.vf.api.lambda.assignment;

import dev.olegz.vf.api.web.support.ActionContextFactory;
import dev.olegz.vf.api.web.support.ActionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import static dev.olegz.vf.api.web.ApiHeaders.API_KEY;

@RestController
@RequestMapping(path = "/vf/lambda-assignments", produces = MediaType.APPLICATION_JSON_VALUE)
public class LambdaAssignmentController {
    private final LambdaAssignmentAction lambdaAssignmentAction;
    private final ActionContextFactory contextFactory;

    public LambdaAssignmentController(LambdaAssignmentAction lambdaAssignmentAction, ActionContextFactory contextFactory) {
        this.lambdaAssignmentAction = lambdaAssignmentAction;
        this.contextFactory = contextFactory;
    }

    @RequestMapping(method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ActionResponse createLambdaAssignment(
        @RequestHeader(API_KEY) String key,
        @Valid @RequestBody LambdaAssignmentAction.CreateRequest request)
    {
        return lambdaAssignmentAction.createLambdaAssignment(contextFactory.current(), request);
    }

    @RequestMapping(method = RequestMethod.GET, path = "{lambdaAssignmentId}")
    public ActionResponse getLambdaAssignment(
        @RequestHeader(API_KEY) String key,
        @PathVariable @Positive int lambdaAssignmentId)
    {
        return lambdaAssignmentAction.getLambdaAssignment(contextFactory.current(), lambdaAssignmentId);
    }

    @RequestMapping(method = RequestMethod.POST, path = "{lambdaAssignmentId}/lambda-api-key")
    public ActionResponse generateLambdaApiKey(
        @RequestHeader(API_KEY) String key,
        @PathVariable @Positive int lambdaAssignmentId)
    {
        return lambdaAssignmentAction.generateLambdaApiKey(contextFactory.current(), lambdaAssignmentId);
    }

    @RequestMapping(method = RequestMethod.GET)
    public ActionResponse getLambdaAssignments(
        @RequestHeader(API_KEY) String key,
        @RequestParam(required = false) @Positive Integer lambdaId,
        @RequestParam(required = false) @Positive Integer locationId)
    {
        return lambdaAssignmentAction.getLambdaAssignments(contextFactory.current(), lambdaId, locationId);
    }

    @RequestMapping(method = RequestMethod.PUT, path = "{lambdaAssignmentId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ActionResponse updateLambdaAssignment(
        @RequestHeader(API_KEY) String key,
        @PathVariable @Positive int lambdaAssignmentId,
        @Valid @RequestBody LambdaAssignmentAction.UpdateRequest request)
    {
        return lambdaAssignmentAction.updateLambdaAssignment(contextFactory.current(), lambdaAssignmentId, request);
    }

    @RequestMapping(method = RequestMethod.DELETE, path = "{lambdaAssignmentId}")
    public ActionResponse cancelLambdaAssignment(
        @RequestHeader(API_KEY) String key,
        @PathVariable @Positive int lambdaAssignmentId)
    {
        return lambdaAssignmentAction.cancelLambdaAssignment(contextFactory.current(), lambdaAssignmentId);
    }

    @RequestMapping(method = RequestMethod.DELETE)
    public ActionResponse cancelLambdaAssignments(
        @RequestHeader(API_KEY) String key,
        @RequestParam(required = false) @Positive Integer lambdaId,
        @RequestParam(required = false) @Positive Integer locationId)
    {
        return lambdaAssignmentAction.cancelLambdaAssignments(contextFactory.current(), lambdaId, locationId);
    }
}
