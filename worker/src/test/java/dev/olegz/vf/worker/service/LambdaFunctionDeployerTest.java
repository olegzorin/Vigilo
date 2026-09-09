package dev.olegz.vf.worker.service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import dev.olegz.vf.aws.local.LocalLambdaClient;
import dev.olegz.vf.core.domain.lambdabuild.BuildConfig;
import dev.olegz.vf.core.domain.lambdabuild.InvocationLaneSettings;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import dev.olegz.vf.worker.domain.UpdateFunctionParams;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.lambda.model.LoggingConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LambdaFunctionDeployerTest {

    @Test
    void deploysDefaultAndAsyncAsIndependentFunctions() {
        LambdaFunctionDeployer deployer = new LambdaFunctionDeployer();
        String baseName = "test-lambda-" + UUID.randomUUID();
        String asyncName = baseName + "-async";
        String imageUri = "test-image:1";
        UpdateFunctionParams defaultParams = new UpdateFunctionParams(
            InvocationLane.DEFAULT, baseName, "test lambda", BuildConfig.ARCH_X86, imageUri, "default",
            new InvocationLaneSettings(1024, 30));
        UpdateFunctionParams asyncParams = new UpdateFunctionParams(
            InvocationLane.ASYNC, asyncName, "test lambda (async)", BuildConfig.ARCH_X86, imageUri, "ASYNC",
            new InvocationLaneSettings(10240, 900));

        try (LocalLambdaClient client = new LocalLambdaClient()) {
            long endTime = Instant.now().getEpochSecond() + 30;
            AtomicReference<String> defaultFunctionRef = new AtomicReference<>();
            AtomicReference<String> asyncFunctionRef = new AtomicReference<>();
            String version = deployer.deployFunction(
                client, defaultParams, "test-role", LoggingConfig.builder().build(), endTime,
                defaultFunctionRef::set);
            String asyncVersion = deployer.deployFunction(
                client, asyncParams, "test-role", LoggingConfig.builder().build(), endTime,
                asyncFunctionRef::set);

            var defaultFunction = client.getFunctionConfiguration(r -> r.functionName(baseName).qualifier(version));
            var asyncFunction = client.getFunctionConfiguration(r -> r.functionName(asyncName).qualifier(asyncVersion));
            assertEquals("1", version);
            assertEquals("1", asyncVersion);
            assertEquals(baseName + ":1", defaultFunctionRef.get());
            assertEquals(asyncName + ":1", asyncFunctionRef.get());
            assertEquals(1024, defaultFunction.memorySize());
            assertEquals(30, defaultFunction.timeout());
            assertEquals(10240, asyncFunction.memorySize());
            assertEquals(900, asyncFunction.timeout());

            client.deleteFunction(r -> r.functionName(baseName));
            client.deleteFunction(r -> r.functionName(asyncName));
        }
    }
}
