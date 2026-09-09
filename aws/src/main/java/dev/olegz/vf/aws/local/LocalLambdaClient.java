package dev.olegz.vf.aws.local;

import java.lang.Runtime;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import dev.olegz.vf.common.io.ProcessRunner;
import dev.olegz.vf.common.props.PropertyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.*;
import software.amazon.awssdk.services.lambda.waiters.LambdaWaiter;

/**
 * Local implementation of {@link LambdaClient} backed by Docker + the AWS Lambda Runtime Interface
 * Emulator (RIE). An in-memory model holds each function's image/config and its published versions;
 * {@code createFunction}/{@code updateFunctionCode}/{@code updateFunctionConfiguration}/
 * {@code publishVersion}/{@code putFunctionEventInvokeConfig}/{@code listVersionsByFunction}/
 * {@code deleteFunction} mutate that model only. {@code getFunctionConfiguration} always reports
 * {@link State#ACTIVE} + {@link LastUpdateStatus#SUCCESSFUL}, which is what the default
 * {@link LambdaClient#waiter()} polls - so no custom waiter is needed (the deployer's
 * {@code waitUntilFunctionUpdated}/{@code waitUntilPublishedVersionActive} resolve on the first poll).
 * <p>
 * {@code invoke} resolves the image for {@code functionName[:qualifier]}, lazily starts a RIE
 * container for it, and POSTs the payload to the emulator's invocation endpoint, returning the lambda
 * output as {@link InvokeResponse#payload()} with a synthetic {@code x-amzn-RequestId} response
 * header (read by {@code LambdaFunctionInvoker}).
 * <p>
 * <b>Async limitation:</b> {@code REQUEST_RESPONSE} (synchronous) invoke is fully supported.
 * {@code EVENT} (async) invoke runs the lambda best-effort but cannot deliver the result anywhere: the
 * async destination is the {@code dev-botlab-lambda-results} SQS queue, and local mode has no SQS
 * message transport or lambda-results consumer (Phase 1 SQS is provisioning-only). Full async parity is
 * out of scope here (Phase 2b). See {@code docs/local-aws-phase2-plan.md} §4.
 */
public final class LocalLambdaClient implements LambdaClient {
    private static final Logger logger = LoggerFactory.getLogger(LocalLambdaClient.class);

    private static final String LATEST = "$LATEST";

    /** Result of running a lambda container: the function output payload plus a tail of its logs. */
    record Result(byte[] payload, String logTail) {
    }

    /** Container runtime for lambda images, overridable in tests where there is no daemon or RIE. */
    interface Invoker {
        Result invoke(String functionName, String qualifier, String imageUri, byte[] payload, String requestId);

        default void stop(String functionName, String qualifier) {
        }

        default void stopAll(String functionName) {
        }
    }

    /** In-memory model of one Lambda function and its published versions. */
    private static final class FunctionModel {
        volatile String imageUri;
        volatile Architecture arch;
        volatile int memory;
        volatile int timeout;
        volatile String role;
        final AtomicInteger versionCounter = new AtomicInteger(0);
        // Published version number -> the image it was published from.
        final Map<String, String> versionImages = new ConcurrentHashMap<>();
    }

    private static final AtomicBoolean asyncWarningLogged = new AtomicBoolean(false);

    // The local "Lambda service" is a single logical backend: deploy (LambdaFunctionDeployer) and invoke
    // (LambdaFunctionInvoker) use separate client instances, so the function model and the container
    // runtime must be shared across every client created via the no-arg (production) constructor -
    // otherwise a deployed function is invisible at invoke time. Tests use the package-private
    // constructor, which gets an isolated model and a stub invoker.
    private static final Map<String, FunctionModel> SHARED_FUNCTIONS = new ConcurrentHashMap<>();
    private static volatile Invoker sharedInvoker;

    private final Invoker invoker;
    private final Map<String, FunctionModel> functions;

    public LocalLambdaClient() {
        this.functions = SHARED_FUNCTIONS;
        this.invoker = sharedInvoker();
    }

    LocalLambdaClient(Invoker invoker) {
        this.functions = new ConcurrentHashMap<>();
        this.invoker = invoker;
    }

    private static Invoker sharedInvoker() {
        Invoker inv = sharedInvoker;
        if (inv == null) {
            synchronized (LocalLambdaClient.class) {
                inv = sharedInvoker;
                if (inv == null) {
                    sharedInvoker = inv = new RieInvoker();
                }
            }
        }
        return inv;
    }

    /* ---------- function lifecycle (in-memory model) ---------- */

    @Override
    public CreateFunctionResponse createFunction(CreateFunctionRequest request) {
        FunctionModel model = new FunctionModel();
        model.imageUri = request.code() == null ? null : request.code().imageUri();
        model.arch = firstArch(request.architectures());
        model.memory = request.memorySize() == null ? 0 : request.memorySize();
        model.timeout = request.timeout() == null ? 0 : request.timeout();
        model.role = request.role();
        functions.put(request.functionName(), model);

        return CreateFunctionResponse.builder()
            .functionName(request.functionName())
            .functionArn(functionArn(request.functionName()))
            .version(LATEST)
            .memorySize(model.memory)
            .timeout(model.timeout)
            .role(model.role)
            .state(State.ACTIVE)
            .lastUpdateStatus(LastUpdateStatus.SUCCESSFUL)
            .build();
    }

    @Override
    public UpdateFunctionCodeResponse updateFunctionCode(UpdateFunctionCodeRequest request) {
        FunctionModel model = require(request.functionName());
        model.imageUri = request.imageUri();
        Architecture arch = firstArch(request.architectures());
        if (arch != null) model.arch = arch;

        return UpdateFunctionCodeResponse.builder()
            .functionName(request.functionName())
            .functionArn(functionArn(request.functionName()))
            .version(LATEST)
            .state(State.ACTIVE)
            .lastUpdateStatus(LastUpdateStatus.SUCCESSFUL)
            .build();
    }

    @Override
    public UpdateFunctionConfigurationResponse updateFunctionConfiguration(UpdateFunctionConfigurationRequest request) {
        FunctionModel model = require(request.functionName());
        if (request.memorySize() != null) model.memory = request.memorySize();
        if (request.timeout() != null) model.timeout = request.timeout();
        if (request.role() != null) model.role = request.role();

        return UpdateFunctionConfigurationResponse.builder()
            .functionName(request.functionName())
            .functionArn(functionArn(request.functionName()))
            .version(LATEST)
            .memorySize(model.memory)
            .timeout(model.timeout)
            .role(model.role)
            .state(State.ACTIVE)
            .lastUpdateStatus(LastUpdateStatus.SUCCESSFUL)
            .build();
    }

    @Override
    public PublishVersionResponse publishVersion(PublishVersionRequest request) {
        FunctionModel model = require(request.functionName());
        String version = Integer.toString(model.versionCounter.incrementAndGet());
        model.versionImages.put(version, model.imageUri);

        return PublishVersionResponse.builder()
            .functionName(request.functionName())
            .functionArn(functionArn(request.functionName()) + ':' + version)
            .version(version)
            .memorySize(model.memory)
            .timeout(model.timeout)
            .role(model.role)
            .state(State.ACTIVE)
            .lastUpdateStatus(LastUpdateStatus.SUCCESSFUL)
            .build();
    }

    @Override
    public PutFunctionEventInvokeConfigResponse putFunctionEventInvokeConfig(PutFunctionEventInvokeConfigRequest request) {
        // Async result routing has no local transport (see class-level async limitation); accept and ignore.
        require(request.functionName());
        return PutFunctionEventInvokeConfigResponse.builder().build();
    }

    @Override
    public ListVersionsByFunctionResponse listVersionsByFunction(ListVersionsByFunctionRequest request) {
        FunctionModel model = require(request.functionName());
        List<FunctionConfiguration> versions = new ArrayList<>();
        versions.add(versionConfig(request.functionName(), LATEST, model));
        for (String version : model.versionImages.keySet()) {
            versions.add(versionConfig(request.functionName(), version, model));
        }
        return ListVersionsByFunctionResponse.builder().versions(versions).build();
    }

    @Override
    public DeleteFunctionResponse deleteFunction(DeleteFunctionRequest request) {
        String name = request.functionName();
        String qualifier = request.qualifier();
        if (qualifier != null) {
            FunctionModel model = require(name);
            model.versionImages.remove(qualifier);
            invoker.stop(name, qualifier);
        } else {
            if (functions.remove(name) == null) {
                throw ResourceNotFoundException.builder().message("Function not found: " + name).build();
            }
            invoker.stopAll(name);
        }
        return DeleteFunctionResponse.builder().build();
    }

    /**
     * The interface default {@link LambdaClient#waiter()} throws {@link UnsupportedOperationException}
     * (only the real {@code DefaultLambdaClient} overrides it), so it must be overridden here. No
     * custom waiter logic is needed: the standard {@link LambdaWaiter} polls
     * {@link #getFunctionConfiguration}, which always reports ACTIVE/SUCCESSFUL, so the deployer's
     * waits resolve on the first poll.
     */
    @Override
    public LambdaWaiter waiter() {
        return LambdaWaiter.builder().client(this).build();
    }

    @Override
    public GetFunctionConfigurationResponse getFunctionConfiguration(GetFunctionConfigurationRequest request) {
        FunctionModel model = require(request.functionName());
        String version = request.qualifier() == null ? LATEST : request.qualifier();
        return GetFunctionConfigurationResponse.builder()
            .functionName(request.functionName())
            .functionArn(functionArn(request.functionName()))
            .version(version)
            .memorySize(model.memory)
            .timeout(model.timeout)
            .role(model.role)
            .state(State.ACTIVE)
            .lastUpdateStatus(LastUpdateStatus.SUCCESSFUL)
            .build();
    }

    /* ---------- invocation ---------- */

    @Override
    public InvokeResponse invoke(InvokeRequest request) {
        String name = request.functionName();
        String qualifier = request.qualifier();
        // Lambda allows the qualifier to be carried in the name as "<name>:<qualifier>".
        if (qualifier == null && name != null) {
            int colon = name.indexOf(':');
            if (colon >= 0) {
                qualifier = name.substring(colon + 1);
                name = name.substring(0, colon);
            }
        }

        FunctionModel model = require(name);
        String imageUri = qualifier == null || LATEST.equals(qualifier)
            ? model.imageUri : model.versionImages.get(qualifier);
        if (imageUri == null) {
            throw ResourceNotFoundException.builder()
                .message("Function version not found: " + name + ':' + qualifier).build();
        }

        boolean async = request.invocationType() == InvocationType.EVENT;
        if (async && asyncWarningLogged.compareAndSet(false, true)) {
            logger.warn("Local Lambda async (EVENT) invoke runs the lambda but cannot deliver the result: " +
                "local mode has no SQS lambda-results transport (see docs/local-aws-phase2-plan.md §4)");
        }

        byte[] payload = request.payload() == null ? new byte[0] : request.payload().asByteArray();
        // Generate the AWS request id up front and pass it to the invoker so RIE (v1.33+) adopts it as the
        // invoke id it logs (START/END/REPORT RequestId), instead of minting its own. The same id is returned
        // in the x-amzn-RequestId response header below, giving one id end-to-end so server logs correlate
        // with the lambda's RIE logs. Older RIE binaries ignore the request header and log their own id.
        String requestId = UUID.randomUUID().toString();
        Result result = invoker.invoke(name, qualifier, imageUri, payload, requestId);

        SdkHttpResponse httpResponse = SdkHttpResponse.builder()
            .statusCode(async ? 202 : 200)
            .appendHeader("x-amzn-RequestId", requestId)
            .build();

        InvokeResponse.Builder builder = InvokeResponse.builder()
            .statusCode(async ? 202 : 200)
            .payload(SdkBytes.fromByteArray(result.payload() == null ? new byte[0] : result.payload()));
        if (request.logType() == LogType.TAIL && result.logTail() != null) {
            builder.logResult(Base64.getEncoder().encodeToString(result.logTail().getBytes(StandardCharsets.UTF_8)));
        }
        // sdkHttpResponse(...) returns the base SdkResponse.Builder, so set it as a statement and
        // build from the typed builder, which has been mutated in place.
        builder.sdkHttpResponse(httpResponse);
        return builder.build();
    }

    /* ---------- helpers ---------- */

    private FunctionModel require(String functionName) {
        FunctionModel model = functions.get(functionName);
        if (model == null) {
            throw ResourceNotFoundException.builder().message("Function not found: " + functionName).build();
        }
        return model;
    }

    private static FunctionConfiguration versionConfig(String name, String version, FunctionModel model) {
        return FunctionConfiguration.builder()
            .functionName(name)
            .functionArn(functionArn(name) + ':' + version)
            .version(version)
            .memorySize(model.memory)
            .timeout(model.timeout)
            .role(model.role)
            .state(State.ACTIVE)
            .lastUpdateStatus(LastUpdateStatus.SUCCESSFUL)
            .build();
    }

    private static Architecture firstArch(List<Architecture> architectures) {
        return architectures == null || architectures.isEmpty() ? null : architectures.getFirst();
    }

    private static String functionArn(String name) {
        return "arn:aws:lambda:local:" + LocalAws.ACCOUNT_ID + ":function:" + name;
    }

    @Override
    public String serviceName() {
        return LambdaClient.SERVICE_NAME;
    }

    @Override
    public void close() {
    }

    /* ---------------------------------------------------------------------------------------------
     * Production invoker: runs each lambda image as a RIE container and POSTs the payload to it.
     * Exercised only in manual integration runs (needs a Docker daemon + the aws-lambda-rie binary);
     * unit tests substitute a stub Invoker.
     * ------------------------------------------------------------------------------------------- */
    private static final class RieInvoker implements Invoker {
        private static final String INVOCATIONS_PATH = "/2015-03-31/functions/function/invocations";
        private static final long RUN_TIMEOUT_MS = 60_000L;
        private static final long STOP_TIMEOUT_MS = 30_000L;
        private static final int READINESS_ATTEMPTS = 60;
        private static final long READINESS_DELAY_MS = 500L;

        private record Container(String id, int port) {
        }

        private final Map<String, Container> containers = new ConcurrentHashMap<>();
        private final HttpClient httpClient = HttpClient.newHttpClient();
        private final int portMin;
        private final int portMax;
        private final AtomicInteger nextPort;

        private RieInvoker() {
            int[] range = parsePortRange(PropertyStore.getString("vf.aws.local.lambda.portRange", "9000-9100"));
            this.portMin = range[0];
            this.portMax = range[1];
            this.nextPort = new AtomicInteger(range[0]);
            Runtime.getRuntime().addShutdownHook(new Thread(this::stopEverything, "local-lambda-shutdown"));
        }

        @Override
        public Result invoke(String functionName, String qualifier, String imageUri, byte[] payload, String requestId) {
            Container container = containers.computeIfAbsent(key(functionName, qualifier),
                k -> startContainer(imageUri));
            byte[] response = post(container.port(), payload, requestId);
            return new Result(response, logs(container.id()));
        }

        @Override
        public void stop(String functionName, String qualifier) {
            stopContainer(containers.remove(key(functionName, qualifier)));
        }

        @Override
        public void stopAll(String functionName) {
            String prefix = functionName + ':';
            containers.keySet().removeIf(k -> {
                if (k.startsWith(prefix)) {
                    stopContainer(containers.get(k));
                    return true;
                }
                return false;
            });
        }

        private Container startContainer(String imageUri) {
            int port = allocatePort();
            List<String> args = new ArrayList<>(List.of("run", "-d", "--label", "vf-local-lambda",
                "-p", port + ":8080"));
            String rie = PropertyStore.getString("vf.aws.local.lambda.rie");
            String handler = PropertyStore.getString("vf.aws.local.lambda.handler", "lambda.lambda_handler");
            if (rie != null && !rie.isBlank()) {
                args.add("-v");
                args.add(rie + ":/aws-lambda-rie");
                args.add("--entrypoint");
                args.add("/aws-lambda-rie");
                args.add(imageUri);
                // RIE wraps the image's normal Lambda entrypoint, then the handler.
                args.add(PropertyStore.getString("vf.aws.local.lambda.entrypoint", "/lambda-entrypoint.sh"));
                args.add(handler);
            } else {
                // Image is expected to already bundle the runtime interface emulator/client.
                args.add(imageUri);
                args.add(handler);
            }

            ProcessRunner.CompletedProcess cp = LocalDocker.run(args, RUN_TIMEOUT_MS);
            if (!cp.isOk()) {
                throw new IllegalStateException("Failed to start lambda container for " + imageUri + ": " + cp.getLog());
            }
            String containerId = cp.getOut() == null ? "" : cp.getOut().trim();
            awaitReady(port);
            logger.info("Started local lambda container {} for image {} on port {}", containerId, imageUri, port);
            return new Container(containerId, port);
        }

        private void awaitReady(int port) {
            for (int attempt = 0; attempt < READINESS_ATTEMPTS; attempt++) {
                try {
                    post(port, "{}".getBytes(StandardCharsets.UTF_8), null);
                    return;
                } catch (RuntimeException e) {
                    try {
                        Thread.sleep(READINESS_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }

        private byte[] post(int port, byte[] payload, String requestId) {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + INVOCATIONS_PATH))
                .timeout(Duration.ofSeconds(900))
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload));
            if (requestId != null) {
                // RIE v1.33+ reads this header and uses it as the invoke id it logs; older binaries ignore it.
                builder.header("X-Amzn-RequestId", requestId);
            }
            HttpRequest request = builder.build();
            try {
                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                return response.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while invoking lambda container on port " + port, e);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to invoke lambda container on port " + port + ": " + e, e);
            }
        }

        private String logs(String containerId) {
            if (containerId == null || containerId.isBlank()) return null;
            ProcessRunner.CompletedProcess cp = LocalDocker.run(List.of("logs", "--tail", "50", containerId), STOP_TIMEOUT_MS);
            String out = cp.getOut();
            String log = cp.getLog();
            return out == null || out.isBlank() ? log : out;
        }

        private void stopContainer(Container container) {
            if (container == null) return;
            LocalDocker.run(List.of("rm", "-f", container.id()), STOP_TIMEOUT_MS);
        }

        private void stopEverything() {
            for (Container container : containers.values()) {
                stopContainer(container);
            }
            containers.clear();
        }

        private synchronized int allocatePort() {
            for (int i = portMin; i <= portMax; i++) {
                int port = nextPort.getAndUpdate(p -> p >= portMax ? portMin : p + 1);
                boolean used = false;
                for (Container c : containers.values()) {
                    if (c.port() == port) {
                        used = true;
                        break;
                    }
                }
                if (!used) return port;
            }
            throw new IllegalStateException("No free local Lambda port in range " + portMin + '-' + portMax);
        }

        private static int[] parsePortRange(String range) {
            String[] parts = range.split("-");
            try {
                int min = Integer.parseInt(parts[0].trim());
                int max = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : min;
                return max >= min ? new int[]{min, max} : new int[]{min, min};
            } catch (RuntimeException e) {
                return new int[]{9000, 9100};
            }
        }

        private static String key(String functionName, String qualifier) {
            return functionName + ':' + (qualifier == null ? LATEST : qualifier);
        }
    }
}
