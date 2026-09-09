package dev.olegz.vf.core.service.lambda;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.common.exception.WrongParameterValueException;
import dev.olegz.vf.common.objectmap.StringMapper;
import dev.olegz.vf.core.dao.LambdaAlertDao;
import dev.olegz.vf.core.domain.alert.*;
import dev.olegz.vf.core.domain.lambdarun.LambdaKey;
import dev.olegz.vf.registry.dao.handlers.JsonTypeHandler;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class LambdaAlertServiceImpl implements LambdaAlertService {
    static final int MAX_CHANGES_JSON_LENGTH = 16_000;

    private final LambdaClientService lambdaClientService;
    private final LambdaAlertDao lambdaAlertDao;

    public LambdaAlertServiceImpl(LambdaClientService lambdaClientService, LambdaAlertDao lambdaAlertDao) {
        this.lambdaClientService = lambdaClientService;
        this.lambdaAlertDao = lambdaAlertDao;
    }

    @Override
    public LambdaAlertResult createAlert(String lambdaApiKey, LambdaAlertSubmission submission) {
        validateSubmission(submission);
        LambdaKey lambdaKey = lambdaClientService.parseLambdaKey(lambdaApiKey);
        authorize(lambdaKey, submission);

        String changesJson = StringMapper.toString(submission.reasons);
        if (changesJson.length() > MAX_CHANGES_JSON_LENGTH ||
            !JsonTypeHandler.isValidVarchar(changesJson))
        {
            throw new WrongParameterValueException(
                "Alert reasons are too large or contain unsupported characters");
        }

        LambdaAlert alert = toAlert(lambdaKey, submission, changesJson);
        try {
            lambdaAlertDao.insertAlert(alert);
            return new LambdaAlertResult(alert, true);
        } catch (DuplicateKeyException duplicate) {
            LambdaAlert existing = lambdaAlertDao.getAlert(
                submission.idempotencyKey, lambdaKey.lambdaAssignmentId);
            if (existing == null || !alert.payloadHash.equals(existing.payloadHash)) {
                throw new WrongParameterValueException(
                    "Idempotency key is already used for a different alert");
            }
            return new LambdaAlertResult(existing, false);
        }
    }

    private void authorize(LambdaKey lambdaKey, LambdaAlertSubmission submission) {
        if (!lambdaClientService.checkLambdaLocationAccess(lambdaKey, submission.locationId)) {
            throw new AccessDeniedException(
                "Access to location " + submission.locationId + " denied");
        }

        for (LambdaAlertReason reason : submission.reasons) {
            if (reason.resourceType == LambdaAlertResourceType.LOCATION) {
                if (!Integer.toString(submission.locationId).equals(reason.resourceId)) {
                    throw new AccessDeniedException(
                        "Alert reason references another location");
                }
            } else if (!lambdaClientService.checkLambdaDeviceAccess(lambdaKey, reason.resourceId)) {
                throw new AccessDeniedException(
                    "Access to device " + reason.resourceId + " denied");
            }
        }
    }

    private static LambdaAlert toAlert(
        LambdaKey lambdaKey,
        LambdaAlertSubmission submission,
        String changesJson)
    {
        LambdaAlert alert = new LambdaAlert();
        alert.alertId = UUID.randomUUID().toString();
        alert.idempotencyKey = submission.idempotencyKey;
        alert.payloadHash = payloadHash(submission);
        alert.locationId = submission.locationId;
        alert.deviceUuid = singleDevice(submission);
        alert.alertType = "DANGEROUS_STATE_CHANGE";
        alert.severity = submission.severity;
        alert.status = "OPEN";
        alert.ruleId = submission.ruleId;
        alert.occurredAt = new Timestamp(submission.occurredAt);
        alert.lambdaAssignmentId = lambdaKey.lambdaAssignmentId;
        alert.lambdaId = lambdaKey.lambdaId;
        alert.lambdaVersionId = lambdaKey.lambdaVersionId();
        alert.runId = submission.runId;
        alert.eventKey = submission.eventKey;
        alert.changesJson = changesJson;
        return alert;
    }

    private static String singleDevice(LambdaAlertSubmission submission) {
        Set<String> devices = new HashSet<>();
        for (LambdaAlertReason reason : submission.reasons) {
            if (reason.resourceType == LambdaAlertResourceType.DEVICE) {
                devices.add(reason.resourceId);
            }
        }
        return devices.size() == 1 ? devices.iterator().next() : null;
    }

    private static String payloadHash(LambdaAlertSubmission submission) {
        try {
            byte[] bytes = StringMapper.toString(submission).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new ApplicationFailureException("SHA-256 is not available", e);
        }
    }

    private static void validateSubmission(LambdaAlertSubmission submission) {
        if (submission == null) {
            throw new WrongParameterValueException("Alert is required");
        }
        requireText(submission.idempotencyKey, 250, "idempotencyKey");
        requireText(submission.ruleId, 100, "ruleId");
        requireText(submission.eventKey, 100, "eventKey");
        if (submission.severity == null) {
            throw new WrongParameterValueException("severity is required");
        }
        if (submission.locationId <= 0 || submission.occurredAt <= 0 || submission.runId <= 0) {
            throw new WrongParameterValueException(
                "locationId, occurredAt, and runId must be positive");
        }
        if (submission.reasons == null || submission.reasons.isEmpty() || submission.reasons.size() > 20) {
            throw new WrongParameterValueException("reasons must contain between 1 and 20 items");
        }
        for (LambdaAlertReason reason : submission.reasons) {
            if (reason == null || reason.resourceType == null) {
                throw new WrongParameterValueException("reason.resourceType is required");
            }
            requireText(reason.resourceId, 150, "reason.resourceId");
            requireText(reason.field, 100, "reason.field");
        }
    }

    private static void requireText(String value, int maxLength, String name) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new WrongParameterValueException(
                name + " must contain between 1 and " + maxLength + " characters");
        }
    }
}
