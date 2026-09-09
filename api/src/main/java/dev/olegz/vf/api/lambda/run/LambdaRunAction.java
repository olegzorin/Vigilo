package dev.olegz.vf.api.lambda.run;

import dev.olegz.vf.api.web.support.ActionResponse;
import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.core.domain.lambdarun.LambdaKey;
import dev.olegz.vf.core.service.lambda.LambdaClientService;
import org.springframework.stereotype.Component;

@Component
public class LambdaRunAction {
    private final LambdaClientService lambdaClientService;

    public LambdaRunAction(LambdaClientService lambdaClientService) {
        this.lambdaClientService = lambdaClientService;
    }

    public ActionResponse startRun(
        String lambdaApiKey,
        long invocationToken,
        String awsRequestId,
        String logStreamName)
    {
        LambdaKey lambdaKey = lambdaClientService.parseLambdaKey(lambdaApiKey);
        if (!lambdaClientService.startRun(lambdaKey, invocationToken, awsRequestId, logStreamName)) {
            throw new AccessDeniedException("Lambda run start rejected");
        }
        return new ActionResponse();
    }
}
