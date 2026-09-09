package dev.olegz.vf.core.domain.lambdabuild;

import java.lang.reflect.Constructor;

import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LambdaCodeUploadTest {

    @Test
    void functionNamesUseSeparateDefaultAndAsyncFunctions() throws ReflectiveOperationException {
        Constructor<LambdaCodeUpload> constructor = LambdaCodeUpload.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        LambdaCodeUpload upload = constructor.newInstance();
        upload.lambdaId = 12;
        upload.lambdaVersionId = 34;
        upload.asyncFunctionName = "persisted-async-function:1";

        assertEquals("dev-botlab-lambda-12-34", upload.getBaseFunctionName(InvocationLane.DEFAULT));
        assertEquals("dev-botlab-lambda-12-34-async", upload.getBaseFunctionName(InvocationLane.ASYNC));
        assertEquals("persisted-async-function:1", upload.getFunctionName(InvocationLane.ASYNC));
    }
}
