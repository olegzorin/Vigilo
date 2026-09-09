package dev.olegz.vf.api.lambda.alert;

import java.util.List;

import dev.olegz.vf.common.objectmap.StringMapper;
import dev.olegz.vf.core.domain.alert.*;
import dev.olegz.vf.core.service.lambda.LambdaAlertService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LambdaAlertActionTest {

    @Test
    void mapsRequestAndReturnsIdempotentCreationResult() {
        LambdaAlertSubmission[] captured = new LambdaAlertSubmission[1];
        LambdaAlertService service = (key, submission) -> {
            assertEquals("lambda-key", key);
            captured[0] = submission;
            LambdaAlert alert = new LambdaAlert();
            alert.alertId = "alert-1";
            alert.status = "OPEN";
            return new LambdaAlertResult(alert, true);
        };
        LambdaAlertAction action = new LambdaAlertAction(service);
        LambdaAlertAction.Request request = request();

        LambdaAlertAction.Response response = action.createAlert("lambda-key", request);

        assertEquals("alert-1", response.alertId);
        assertEquals("OPEN", response.status);
        assertTrue(response.created);
        assertEquals("danger-v1", captured[0].ruleId);
        assertEquals(LambdaAlertResourceType.DEVICE, captured[0].reasons.getFirst().resourceType);
        assertEquals(true, captured[0].reasons.getFirst().newValue);
    }

    @Test
    void deserializesDocumentedAlertRequest() {
        LambdaAlertAction.Request request = StringMapper.readValue(
            """
            {
              "idempotencyKey": "101:event-1:danger-v1",
              "ruleId": "danger-v1",
              "severity": "CRITICAL",
              "locationId": 11,
              "occurredAt": 1750000000000,
              "runId": 1750000001000,
              "eventKey": "event-1",
              "reasons": [{
                "resourceType": "DEVICE",
                "resourceId": "device-1",
                "field": "alarm",
                "previousValue": false,
                "newValue": true
              }]
            }
            """,
            LambdaAlertAction.Request.class);

        assertEquals(LambdaAlertSeverity.CRITICAL, request.severity);
        assertEquals(LambdaAlertResourceType.DEVICE, request.reasons.getFirst().resourceType);
        assertEquals(false, request.reasons.getFirst().previousValue);
        assertEquals(true, request.reasons.getFirst().newValue);
    }

    private static LambdaAlertAction.Request request() {
        LambdaAlertAction.Reason reason = new LambdaAlertAction.Reason();
        reason.resourceType = LambdaAlertResourceType.DEVICE;
        reason.resourceId = "device-1";
        reason.field = "alarm";
        reason.previousValue = false;
        reason.newValue = true;

        LambdaAlertAction.Request request = new LambdaAlertAction.Request();
        request.idempotencyKey = "101:event-1:danger-v1";
        request.ruleId = "danger-v1";
        request.severity = LambdaAlertSeverity.CRITICAL;
        request.locationId = 11;
        request.occurredAt = 1_750_000_000_000L;
        request.runId = 1_750_000_001_000L;
        request.eventKey = "event-1";
        request.reasons = List.of(reason);
        return request;
    }
}
