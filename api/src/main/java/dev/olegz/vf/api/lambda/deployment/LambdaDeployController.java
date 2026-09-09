package dev.olegz.vf.api.lambda.deployment;

import dev.olegz.vf.api.web.support.ActionContextFactory;
import dev.olegz.vf.api.web.support.ActionResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import static dev.olegz.vf.api.web.ApiHeaders.API_KEY;

@RestController("lambdaDeployController")
@RequestMapping(path = "/vf/lambdas", produces = MediaType.APPLICATION_JSON_VALUE)
public class LambdaDeployController {

    private final LambdaDeployAction lambdaDeployAction;
    private final ActionContextFactory contextFactory;

    public LambdaDeployController(
        LambdaDeployAction lambdaDeployAction,
        ActionContextFactory contextFactory)
    {
        this.lambdaDeployAction = lambdaDeployAction;
        this.contextFactory = contextFactory;
    }

    @RequestMapping(method = RequestMethod.POST, path = "code-uploads", consumes = {"application/x-tar","application/zip"})
    public ActionResponse postLambdaCode(
        @RequestHeader(API_KEY) String key,
        @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType,
        @RequestParam int lambdaId,
        @RequestParam String buildTarget,
        @RequestBody byte[] content)
    {
        return lambdaDeployAction.postLambdaCode(contextFactory.current(), lambdaId, buildTarget, contentType, content);
    }

    @RequestMapping(method = RequestMethod.GET, path = "code-uploads/{uploadId}")
    public ActionResponse getLambdaCodeUploadResult(
        @RequestHeader(API_KEY) String key,
        @RequestParam int lambdaId,
        @PathVariable int uploadId)
    {
        return lambdaDeployAction.getLambdaCodeUpload(contextFactory.current(), lambdaId, uploadId);
    }

    @RequestMapping(method = RequestMethod.GET, path = "python-image-tags")
    public ActionResponse getPythonImageTags(@RequestHeader(API_KEY) String key) {
        return lambdaDeployAction.getPythonImageTags();
    }
}
