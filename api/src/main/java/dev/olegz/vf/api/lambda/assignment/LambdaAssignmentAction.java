package dev.olegz.vf.api.lambda.assignment;

import java.util.List;

import dev.olegz.vf.api.web.support.ActionContext;
import dev.olegz.vf.api.web.support.ActionResponse;
import dev.olegz.vf.common.exception.MissingParameterException;
import dev.olegz.vf.common.exception.WrongParameterValueException;
import dev.olegz.vf.common.util.CollectionOps;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaAssignment;
import dev.olegz.vf.core.domain.lambdarun.LambdaKeyInput;
import dev.olegz.vf.core.service.lambda.LambdaAssignmentService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.stereotype.Component;

@Component
public class LambdaAssignmentAction {
    private final LambdaAssignmentService lambdaAssignmentService;

    public LambdaAssignmentAction(LambdaAssignmentService lambdaAssignmentService) {
        this.lambdaAssignmentService = lambdaAssignmentService;
    }

    public Response createLambdaAssignment(ActionContext ctx, CreateRequest request) {
        int lambdaAssignmentId = lambdaAssignmentService.createLambdaAssignment(
            ctx.user(), request.lambdaId, request.locationId, request.testing);

        Response response = new Response();
        response.lambdaAssignmentId = lambdaAssignmentId;
        return response;
    }

    public Response getLambdaAssignment(ActionContext ctx, int lambdaAssignmentId) {
        LambdaAssignment assignment = lambdaAssignmentService.getLambdaAssignment(ctx.user(), lambdaAssignmentId);

        Response response = new Response();
        response.assignment = new ApiLambdaAssignment(assignment, null);
        return response;
    }

    public LambdaApiKeyResponse generateLambdaApiKey(ActionContext ctx, int lambdaAssignmentId) {
        LambdaKeyInput key = lambdaAssignmentService.generateLambdaApiKey(ctx.user(), lambdaAssignmentId);

        LambdaApiKeyResponse response = new LambdaApiKeyResponse();
        response.lambdaApiKey = key.key;
        response.expiry = key.expiry;
        return response;
    }

    public Response getLambdaAssignments(ActionContext ctx, Integer lambdaId, Integer locationId) {
        requireOneFilter(lambdaId, locationId);
        List<LambdaAssignment> assignments = lambdaId != null
            ? lambdaAssignmentService.getLambdaAssignmentsForLambda(ctx.user(), lambdaId)
            : lambdaAssignmentService.getLambdaAssignmentsForLocation(ctx.user(), locationId);

        Response response = new Response();
        response.assignments = CollectionOps.map(assignments, assignment -> new ApiLambdaAssignment(assignment, null));
        if (response.assignments == null) response.assignments = List.of();
        response.collectionTotalSize = response.assignments.size();
        return response;
    }

    public ActionResponse updateLambdaAssignment(ActionContext ctx, int lambdaAssignmentId, UpdateRequest request) {
        lambdaAssignmentService.setLambdaAssignmentEnabled(ctx.user(), lambdaAssignmentId, request.enabled);
        return new ActionResponse();
    }

    public ActionResponse cancelLambdaAssignment(ActionContext ctx, int lambdaAssignmentId) {
        lambdaAssignmentService.cancelLambdaAssignment(ctx.user(), lambdaAssignmentId);
        return new ActionResponse();
    }

    public ActionResponse cancelLambdaAssignments(ActionContext ctx, Integer lambdaId, Integer locationId) {
        requireOneFilter(lambdaId, locationId);
        if (lambdaId != null) {
            lambdaAssignmentService.cancelAssignmentsForLambda(ctx.user(), lambdaId);
        } else {
            lambdaAssignmentService.cancelAssignmentsForLocation(ctx.user(), locationId);
        }
        return new ActionResponse();
    }

    private static void requireOneFilter(Integer lambdaId, Integer locationId) {
        if (lambdaId == null && locationId == null) {
            throw new MissingParameterException("Either lambdaId or locationId is required");
        }
        if (lambdaId != null && locationId != null) {
            throw new WrongParameterValueException("Specify either lambdaId or locationId, not both");
        }
    }

    public static class CreateRequest {
        public @Positive(message = "lambdaId") int lambdaId;
        public @Positive(message = "locationId") int locationId;
        public @NotNull(message = "testing") Boolean testing;
    }

    public static class UpdateRequest {
        public @NotNull(message = "enabled") Boolean enabled;
    }

    public static class Response extends ActionResponse {
        public Integer lambdaAssignmentId;
        public ApiLambdaAssignment assignment;
        public List<ApiLambdaAssignment> assignments;
    }

    public static class LambdaApiKeyResponse extends ActionResponse {
        public String lambdaApiKey;
        public long expiry;
    }
}
