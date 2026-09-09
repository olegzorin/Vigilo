package dev.olegz.vf.api.lambda.alert;

import java.util.ArrayList;
import java.util.List;

import dev.olegz.vf.api.web.support.ActionResponse;
import dev.olegz.vf.core.domain.alert.*;
import dev.olegz.vf.core.service.lambda.LambdaAlertService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;

@Component
public class LambdaAlertAction {
    private final LambdaAlertService lambdaAlertService;

    public LambdaAlertAction(LambdaAlertService lambdaAlertService) {
        this.lambdaAlertService = lambdaAlertService;
    }

    public Response createAlert(String lambdaApiKey, Request request) {
        LambdaAlertSubmission submission = new LambdaAlertSubmission();
        submission.idempotencyKey = request.idempotencyKey;
        submission.ruleId = request.ruleId;
        submission.severity = request.severity;
        submission.locationId = request.locationId;
        submission.occurredAt = request.occurredAt;
        submission.runId = request.runId;
        submission.eventKey = request.eventKey;
        submission.reasons = new ArrayList<>(request.reasons.size());
        for (Reason requestReason : request.reasons) {
            LambdaAlertReason reason = new LambdaAlertReason();
            reason.resourceType = requestReason.resourceType;
            reason.resourceId = requestReason.resourceId;
            reason.field = requestReason.field;
            reason.previousValue = requestReason.previousValue;
            reason.newValue = requestReason.newValue;
            submission.reasons.add(reason);
        }

        LambdaAlertResult result = lambdaAlertService.createAlert(lambdaApiKey, submission);
        Response response = new Response();
        response.alertId = result.alert().alertId;
        response.status = result.alert().status;
        response.created = result.created();
        return response;
    }

    public static class Request {
        public @NotBlank(message = "idempotencyKey") @Size(max = 250, message = "idempotencyKey")
            String idempotencyKey;
        public @NotBlank(message = "ruleId") @Size(max = 100, message = "ruleId") String ruleId;
        public @NotNull(message = "severity") LambdaAlertSeverity severity;
        public @Positive(message = "locationId") int locationId;
        public @Positive(message = "occurredAt") long occurredAt;
        public @Positive(message = "runId") long runId;
        public @NotBlank(message = "eventKey") @Size(max = 100, message = "eventKey") String eventKey;
        public @NotEmpty(message = "reasons") @Size(max = 20, message = "reasons")
            List<@Valid Reason> reasons;
    }

    public static class Reason {
        public @NotNull(message = "resourceType") LambdaAlertResourceType resourceType;
        public @NotBlank(message = "resourceId") @Size(max = 150, message = "resourceId")
            String resourceId;
        public @NotBlank(message = "field") @Size(max = 100, message = "field") String field;
        public Object previousValue;
        public Object newValue;
    }

    public static class Response extends ActionResponse {
        public String alertId;
        public String status;
        public boolean created;
    }
}
