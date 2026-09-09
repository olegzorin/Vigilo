package dev.olegz.vf.core.service.lambda;

import dev.olegz.vf.core.domain.alert.LambdaAlertResult;
import dev.olegz.vf.core.domain.alert.LambdaAlertSubmission;

public interface LambdaAlertService {
    LambdaAlertResult createAlert(String lambdaApiKey, LambdaAlertSubmission submission);
}
