package dev.olegz.vf.api.lambda.run;

import dev.olegz.vf.api.web.ApiHeaders;
import dev.olegz.vf.api.web.support.ActionResponse;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController("lambdaRunController")
@RequestMapping(path = "/vf/start", produces = MediaType.APPLICATION_JSON_VALUE)
public class LambdaRunController {
    private final LambdaRunAction lambdaRunAction;

    public LambdaRunController(LambdaRunAction lambdaRunAction) {
        this.lambdaRunAction = lambdaRunAction;
    }

    @RequestMapping(method = RequestMethod.POST)
    public ActionResponse startRun(
        @RequestHeader(ApiHeaders.LAMBDA_API_KEY) String key,
        @RequestParam @Positive long invocationToken,
        @RequestParam(required = false) String awsRequestId,
        @RequestParam(required = false) String logStreamName)
    {
        return lambdaRunAction.startRun(key, invocationToken, awsRequestId, logStreamName);
    }
}
