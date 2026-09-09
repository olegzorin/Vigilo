package dev.olegz.vf.aws.local;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.olegz.vf.aws.LocalAwsTestSupport;
import dev.olegz.vf.common.util.CollectionOps;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.model.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LocalLambdaClient}, stubbing the container runtime so no Docker daemon or
 * RIE is needed. Covers the in-memory function/version model, the default-waiter resolution, and the
 * invoke payload/header round-trip.
 */
class LocalLambdaClientTest extends LocalAwsTestSupport {

    /** Records the last invoke and returns a fixed payload + log tail. */
    private static final class StubInvoker implements LocalLambdaClient.Invoker {
        final AtomicReference<String> lastImageUri = new AtomicReference<>();
        final AtomicReference<String> lastQualifier = new AtomicReference<>();
        final AtomicReference<String> lastRequestId = new AtomicReference<>();
        byte[] payload = "{\"value\":42}".getBytes(StandardCharsets.UTF_8);
        String logTail = "lambda log line";

        @Override
        public LocalLambdaClient.Result invoke(String functionName, String qualifier, String imageUri,
                                               byte[] payload, String requestId) {
            lastImageUri.set(imageUri);
            lastQualifier.set(qualifier);
            lastRequestId.set(requestId);
            return new LocalLambdaClient.Result(this.payload, logTail);
        }
    }

    private static LocalLambdaClient create(LocalLambdaClient.Invoker invoker, String name, String imageUri) {
        LocalLambdaClient client = new LocalLambdaClient(invoker);
        client.createFunction(r -> r.functionName(name)
            .packageType(PackageType.IMAGE)
            .code(c -> c.imageUri(imageUri))
            .memorySize(256).timeout(30).role("arn:aws:iam::local:role/lambda"));
        return client;
    }

    @Test
    void updateCodeOnMissingFunctionThrowsResourceNotFound() {
        // LambdaFunctionDeployer relies on this to fall back to createFunction on first deploy.
        LocalLambdaClient client = new LocalLambdaClient(new StubInvoker());
        assertThrows(ResourceNotFoundException.class,
            () -> client.updateFunctionCode(r -> r.functionName("nope").imageUri("img:1")));
    }

    @Test
    void publishVersionsAndList() {
        LocalLambdaClient client = create(new StubInvoker(), "lambda", "img:1");

        assertEquals("1", client.publishVersion(r -> r.functionName("lambda")).version());
        assertEquals("2", client.publishVersion(r -> r.functionName("lambda")).version());

        List<FunctionConfiguration> versions = client.listVersionsByFunction(r -> r.functionName("lambda")).versions();
        List<String> names = CollectionOps.map(versions, FunctionConfiguration::version);
        assertTrue(names.contains("$LATEST"));
        assertTrue(names.contains("1"));
        assertTrue(names.contains("2"));
    }

    @Test
    void getFunctionConfigurationReportsActiveAndSuccessful() {
        LocalLambdaClient client = create(new StubInvoker(), "lambda", "img:1");
        var config = client.getFunctionConfiguration(r -> r.functionName("lambda"));
        assertEquals(State.ACTIVE, config.state());
        assertEquals(LastUpdateStatus.SUCCESSFUL, config.lastUpdateStatus());
    }

    @Test
    void defaultWaiterResolvesOnFirstPoll() {
        LocalLambdaClient client = create(new StubInvoker(), "lambda", "img:1");
        client.publishVersion(r -> r.functionName("lambda"));

        var updated = client.waiter().waitUntilFunctionUpdated(r -> r.functionName("lambda")).matched();
        assertTrue(updated.response().isPresent());
        assertEquals(LastUpdateStatus.SUCCESSFUL, updated.response().get().lastUpdateStatus());

        var active = client.waiter()
            .waitUntilPublishedVersionActive(r -> r.functionName("lambda").qualifier("1")).matched();
        assertTrue(active.response().isPresent());
        assertEquals(State.ACTIVE, active.response().get().state());
    }

    @Test
    void deleteVersionThenWholeFunction() {
        LocalLambdaClient client = create(new StubInvoker(), "lambda", "img:1");
        client.publishVersion(r -> r.functionName("lambda")); // version 1
        client.publishVersion(r -> r.functionName("lambda")); // version 2

        client.deleteFunction(r -> r.functionName("lambda").qualifier("1"));
        List<String> remaining = CollectionOps.map(
            client.listVersionsByFunction(r -> r.functionName("lambda")).versions(), FunctionConfiguration::version);
        assertFalse(remaining.contains("1"));
        assertTrue(remaining.contains("2"));

        client.deleteFunction(r -> r.functionName("lambda"));
        assertThrows(ResourceNotFoundException.class, () -> client.listVersionsByFunction(r -> r.functionName("lambda")));
    }

    @Test
    void invokeRequestResponseRoundTripsPayloadAndRequestId() {
        StubInvoker invoker = new StubInvoker();
        LocalLambdaClient client = create(invoker, "lambda", "img:latest");
        client.publishVersion(r -> r.functionName("lambda")); // version 1 -> img:latest

        InvokeResponse response = client.invoke(r -> r.functionName("lambda").qualifier("1")
            .invocationType(InvocationType.REQUEST_RESPONSE)
            .logType(LogType.TAIL)
            .payload(SdkBytes.fromUtf8String("{}")));

        assertEquals(200, response.statusCode());
        assertEquals("{\"value\":42}", response.payload().asUtf8String());
        assertEquals("img:latest", invoker.lastImageUri.get());
        assertEquals("1", invoker.lastQualifier.get());

        List<String> requestIds = response.sdkHttpResponse().headers().get("x-amzn-RequestId");
        assertNotNull(requestIds);
        assertFalse(requestIds.isEmpty());
        // The id handed to the invoker (sent to RIE as X-Amzn-RequestId) must match the one we return,
        // so server logs correlate with RIE's START/END/REPORT lines.
        assertEquals(requestIds.getFirst(), invoker.lastRequestId.get());

        String decodedLog = new String(Base64.getDecoder().decode(response.logResult()), StandardCharsets.UTF_8);
        assertEquals("lambda log line", decodedLog);
    }

    @Test
    void invokeUnknownVersionThrowsResourceNotFound() {
        LocalLambdaClient client = create(new StubInvoker(), "lambda", "img:1");
        assertThrows(ResourceNotFoundException.class,
            () -> client.invoke(r -> r.functionName("lambda").qualifier("99")
                .invocationType(InvocationType.REQUEST_RESPONSE)
                .payload(SdkBytes.fromUtf8String("{}"))));
    }

    @Test
    void invokeEventReturns202AndRequestId() {
        LocalLambdaClient client = create(new StubInvoker(), "lambda", "img:1");
        InvokeResponse response = client.invoke(r -> r.functionName("lambda")
            .invocationType(InvocationType.EVENT)
            .payload(SdkBytes.fromUtf8String("{}")));
        assertEquals(202, response.statusCode());
        assertNotNull(response.sdkHttpResponse().headers().get("x-amzn-RequestId"));
    }
}
