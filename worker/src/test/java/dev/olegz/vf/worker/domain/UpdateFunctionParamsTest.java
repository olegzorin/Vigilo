package dev.olegz.vf.worker.domain;

import java.lang.reflect.Constructor;

import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.domain.lambdabuild.LambdaCodeUpload;
import dev.olegz.vf.core.domain.lambdabuild.InvocationLaneSettings;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UpdateFunctionParamsTest {

    @BeforeAll
    static void startPropertyStore() {
        PropertyStore.start();
    }

    @Test
    void defaultAndAsyncFactoriesCreateIndependentConfigurations() throws ReflectiveOperationException {
        LambdaCodeUpload upload = newUpload();

        UpdateFunctionParams defaultParams = UpdateFunctionParams.create(upload, InvocationLane.DEFAULT, "image:1");
        UpdateFunctionParams asyncParams = UpdateFunctionParams.create(upload, InvocationLane.ASYNC, "image:1");
        InvocationLaneSettings defaultSettings = InvocationLaneSettings.forLane(InvocationLane.DEFAULT);
        InvocationLaneSettings asyncSettings = InvocationLaneSettings.forLane(InvocationLane.ASYNC);

        assertNotSame(defaultParams, asyncParams);
        assertAll(
            () -> assertEquals(InvocationLane.DEFAULT, defaultParams.lane),
            () -> assertEquals(InvocationLane.ASYNC, asyncParams.lane),
            () -> assertEquals("dev-botlab-lambda-12-34", defaultParams.functionName),
            () -> assertEquals("dev-botlab-lambda-12-34-async", asyncParams.functionName),
            () -> assertEquals("test lambda, ver.1.2.3", defaultParams.functionDesc),
            () -> assertEquals("test lambda, ver.1.2.3 (async)", asyncParams.functionDesc),
            () -> assertEquals(defaultSettings.memorySize(), defaultParams.memory),
            () -> assertEquals(defaultSettings.timeout(), defaultParams.timeout),
            () -> assertEquals(asyncSettings.memorySize(), asyncParams.memory),
            () -> assertEquals(asyncSettings.timeout(), asyncParams.timeout)
        );
    }

    private static LambdaCodeUpload newUpload() throws ReflectiveOperationException {
        Constructor<LambdaCodeUpload> constructor = LambdaCodeUpload.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        LambdaCodeUpload upload = constructor.newInstance();
        upload.uploadId = 56;
        upload.lambdaId = 12;
        upload.lambdaVersionId = 34;
        upload.lambda = new Lambda();
        upload.lambda.lambdaName = "test lambda";
        upload.lambdaVersion = new LambdaVersion();
        upload.lambdaVersion.version = "1.2.3";
        return upload;
    }
}
