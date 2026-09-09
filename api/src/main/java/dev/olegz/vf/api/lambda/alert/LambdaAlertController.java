package dev.olegz.vf.api.lambda.alert;

import dev.olegz.vf.api.web.ApiHeaders;
import dev.olegz.vf.api.web.support.ActionResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController("lambdaAlertController")
@RequestMapping(path = "/vf/alerts", produces = MediaType.APPLICATION_JSON_VALUE)
public class LambdaAlertController {
    private final LambdaAlertAction lambdaAlertAction;

    public LambdaAlertController(LambdaAlertAction lambdaAlertAction) {
        this.lambdaAlertAction = lambdaAlertAction;
    }

    @RequestMapping(method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ActionResponse createAlert(
        @RequestHeader(ApiHeaders.LAMBDA_API_KEY) String key,
        @Valid @RequestBody LambdaAlertAction.Request request)
    {
        return lambdaAlertAction.createAlert(key, request);
    }
}
