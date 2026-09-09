package dev.olegz.vf.api.lambda.variable;

import dev.olegz.vf.api.web.support.ActionResponse;
import dev.olegz.vf.core.service.lambda.LambdaClientService;
import org.springframework.stereotype.Component;

@Component
public class LambdaVariableAction {
    private final LambdaClientService lambdaClientService;

    public LambdaVariableAction(LambdaClientService lambdaClientService) {
        this.lambdaClientService = lambdaClientService;
    }

    public byte[] getVariable(String lambdaApiKey, String name, boolean shared) {
        return lambdaClientService.getVariable(lambdaApiKey, name, shared);
    }

    public ActionResponse putVariable(String lambdaApiKey, String name, boolean shared, byte[] value) {
        lambdaClientService.putVariable(lambdaApiKey, name, shared, value);
        return new ActionResponse();
    }

    public ActionResponse deleteVariable(String lambdaApiKey, String name, boolean shared) {
        lambdaClientService.deleteVariable(lambdaApiKey, name, shared);
        return new ActionResponse();
    }
}
