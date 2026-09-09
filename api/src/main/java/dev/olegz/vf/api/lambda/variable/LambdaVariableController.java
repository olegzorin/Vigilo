package dev.olegz.vf.api.lambda.variable;

import dev.olegz.vf.api.web.ApiHeaders;
import dev.olegz.vf.api.web.support.ActionResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController("lambdaVariableController")
@RequestMapping(path = "/vf/variables")
public class LambdaVariableController {
    private final LambdaVariableAction lambdaVariableAction;

    public LambdaVariableController(LambdaVariableAction lambdaVariableAction) {
        this.lambdaVariableAction = lambdaVariableAction;
    }

    @RequestMapping(
        method = RequestMethod.GET,
        path = "{name}",
        produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getVariable(
        @RequestHeader(ApiHeaders.LAMBDA_API_KEY) String key,
        @PathVariable String name,
        @RequestParam(defaultValue = "false") boolean shared)
    {
        byte[] value = lambdaVariableAction.getVariable(key, name, shared);
        if ((value == null) || (value.length == 0)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(value.length)
            .body(value);
    }

    @RequestMapping(
        method = RequestMethod.PUT,
        path = "{name}",
        consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ActionResponse putVariable(
        @RequestHeader(ApiHeaders.LAMBDA_API_KEY) String key,
        @PathVariable String name,
        @RequestParam(defaultValue = "false") boolean shared,
        @RequestBody byte[] value)
    {
        return lambdaVariableAction.putVariable(key, name, shared, value);
    }

    @RequestMapping(
        method = RequestMethod.DELETE,
        path = "{name}",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ActionResponse deleteVariable(
        @RequestHeader(ApiHeaders.LAMBDA_API_KEY) String key,
        @PathVariable String name,
        @RequestParam(defaultValue = "false") boolean shared)
    {
        return lambdaVariableAction.deleteVariable(key, name, shared);
    }
}
