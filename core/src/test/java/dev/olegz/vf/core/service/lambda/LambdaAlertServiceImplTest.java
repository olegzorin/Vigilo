package dev.olegz.vf.core.service.lambda;

import java.lang.reflect.Proxy;
import java.util.List;

import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.common.exception.WrongParameterValueException;
import dev.olegz.vf.core.dao.LambdaAlertDao;
import dev.olegz.vf.core.domain.alert.*;
import dev.olegz.vf.core.domain.lambdarun.LambdaKey;
import dev.olegz.vf.core.domain.lambdarun.LambdaKeyJwtClaims;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import static org.junit.jupiter.api.Assertions.*;

class LambdaAlertServiceImplTest {

    @Test
    void createsAuthorizedAlertWithServerDerivedIdentity() {
        LambdaAlert[] inserted = new LambdaAlert[1];
        LambdaAlertDao dao = dao((_, method, args) -> {
            if (method.getName().equals("insertAlert")) {
                inserted[0] = (LambdaAlert) args[0];
            }
            return null;
        });
        LambdaAlertService service = new LambdaAlertServiceImpl(lambdaClientService(true), dao);

        LambdaAlertResult result = service.createAlert("lambda-key", submission("event-1"));

        assertTrue(result.created());
        assertSame(inserted[0], result.alert());
        assertNotNull(result.alert().alertId);
        assertEquals(101, result.alert().lambdaAssignmentId);
        assertEquals(201, result.alert().lambdaId);
        assertEquals(301, result.alert().lambdaVersionId);
        assertEquals("device-1", result.alert().deviceUuid);
        assertEquals("DANGEROUS_STATE_CHANGE", result.alert().alertType);
        assertEquals("OPEN", result.alert().status);
        assertEquals(64, result.alert().payloadHash.length());
    }

    @Test
    void returnsMatchingAlertForDuplicateSubmission() {
        LambdaAlert existing = new LambdaAlert();
        existing.alertId = "existing-alert";
        existing.status = "OPEN";
        LambdaAlertSubmission submission = submission("event-1");
        LambdaAlertDao dao = dao((_, method, args) -> {
            if (method.getName().equals("insertAlert")) {
                LambdaAlert attempted = (LambdaAlert) args[0];
                existing.payloadHash = attempted.payloadHash;
                throw new DuplicateKeyException("duplicate");
            }
            if (method.getName().equals("getAlert")) return existing;
            return null;
        });
        LambdaAlertService service = new LambdaAlertServiceImpl(lambdaClientService(true), dao);

        LambdaAlertResult result = service.createAlert("lambda-key", submission);

        assertFalse(result.created());
        assertSame(existing, result.alert());
    }

    @Test
    void rejectsUnauthorizedDeviceAndReusedKeyWithDifferentPayload() {
        LambdaAlertService unauthorized = new LambdaAlertServiceImpl(
            lambdaClientService(false),
            dao((_, _, _) -> null));
        assertThrows(AccessDeniedException.class,
            () -> unauthorized.createAlert("lambda-key", submission("event-1")));

        LambdaAlert existing = new LambdaAlert();
        existing.payloadHash = "different";
        LambdaAlertDao duplicateDao = dao((_, method, _) -> {
            if (method.getName().equals("insertAlert")) {
                throw new DuplicateKeyException("duplicate");
            }
            return existing;
        });
        LambdaAlertService duplicate = new LambdaAlertServiceImpl(
            lambdaClientService(true), duplicateDao);
        assertThrows(WrongParameterValueException.class,
            () -> duplicate.createAlert("lambda-key", submission("event-1")));
    }

    private static LambdaAlertSubmission submission(String eventKey) {
        LambdaAlertReason reason = new LambdaAlertReason();
        reason.resourceType = LambdaAlertResourceType.DEVICE;
        reason.resourceId = "device-1";
        reason.field = "alarm";
        reason.previousValue = false;
        reason.newValue = true;

        LambdaAlertSubmission submission = new LambdaAlertSubmission();
        submission.idempotencyKey = "101:" + eventKey + ":danger-v1";
        submission.ruleId = "danger-v1";
        submission.severity = LambdaAlertSeverity.CRITICAL;
        submission.locationId = 11;
        submission.occurredAt = 1_750_000_000_000L;
        submission.runId = 1_750_000_001_000L;
        submission.eventKey = eventKey;
        submission.reasons = List.of(reason);
        return submission;
    }

    private static LambdaClientService lambdaClientService(boolean deviceAccess) {
        LambdaKeyJwtClaims claims = new LambdaKeyJwtClaims();
        claims.aid = 101;
        claims.bid = 201;
        claims.bver = 301;
        claims.lid = 11;
        LambdaKey key = new LambdaKey(claims);
        return LambdaClientService.class.cast(Proxy.newProxyInstance(
            LambdaClientService.class.getClassLoader(),
            new Class<?>[]{LambdaClientService.class},
            (_, method, args) -> switch (method.getName()) {
                case "parseLambdaKey" -> key;
                case "checkLambdaLocationAccess" -> (int) args[1] == key.locationId;
                case "checkLambdaDeviceAccess" -> deviceAccess && "device-1".equals(args[1]);
                default -> defaultValue(method.getReturnType());
            }));
    }

    private static LambdaAlertDao dao(java.lang.reflect.InvocationHandler handler) {
        return LambdaAlertDao.class.cast(Proxy.newProxyInstance(
            LambdaAlertDao.class.getClassLoader(),
            new Class<?>[]{LambdaAlertDao.class},
            handler));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
