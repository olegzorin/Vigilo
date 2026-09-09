package dev.olegz.vf.aws.client;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.olegz.vf.aws.config.AwsCredentials;
import dev.olegz.vf.aws.local.*;
import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.props.PropertyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.awscore.defaultsmode.DefaultsMode;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.core.client.builder.SdkSyncClientBuilder;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.internal.util.EC2MetadataUtils;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.retries.api.BackoffStrategy;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecrpublic.EcrPublicClient;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.LambdaClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Factory for AWS SDK v2 clients and builders, pre-configured with the shared credentials
 * provider ({@link AwsCredentials}), the resolved region ({@link #REGION}), and the
 * standard retry strategy (including throttling detection).
 * @see <a href="https://aws.amazon.com/blogs/developer/introducing-smart-configuration-defaults-in-the-aws-sdk-for-java-v2/">Smart Configuration Defaults</a>
 */
public final class AwsClients {

    private static final Logger logger = LoggerFactory.getLogger(AwsClients.class);

    public static final Region REGION = resolveRegion();

    public static final int RETRY_MAX_ATTEMPTS = PropertyStore.getInt("vf.aws.client.retry.maxAttempts", 5);
    public static final long RETRY_BASE_DELAY = PropertyStore.getLong("vf.aws.client.retry.baseDelay", 100L);
    public static final long RETRY_MAX_DELAY = PropertyStore.getLong("vf.aws.client.retry.maxDelay", 3000L);
    public static final long RETRY_BASE_THROTTLING_DELAY = PropertyStore.getLong("vf.aws.client.retry.baseThrottlingDelay", RETRY_BASE_DELAY);
    public static final long RETRY_MAX_THROTTLING_DELAY = PropertyStore.getLong("vf.aws.client.retry.maxThrottlingDelay", RETRY_MAX_DELAY);
    // Overall S3 call budget across all SDK retries (per-attempt is bounded by API_CALL_ATTEMPT_TIMEOUT).
    public static final long S3_API_CALL_TIMEOUT = PropertyStore.getLong("vf.aws.s3.client.maxTimeout", 30_000L);
    // Per-attempt backstop for retrying clients: bounds a single try without truncating retries.
    // Sized above the HTTP connect (5s) + socket (10s) timeouts so it only fires on a stuck attempt.
    public static final long API_CALL_ATTEMPT_TIMEOUT = PropertyStore.getLong("vf.aws.client.apiCallAttemptTimeout", 15_000L);

    private static Region resolveRegion() {
        String propsValue = PropertyStore.getString("vf.aws.region");
        if (propsValue != null) return Region.of(propsValue);

        if (LocalAws.ENABLED) return Region.of("us-east-1");

        try {
            String ec2value = EC2MetadataUtils.getEC2InstanceRegion();
            if (ec2value != null) return Region.of(ec2value);
        } catch (SdkException e) {
            throw new ApplicationFailureException("AWS Region not configured in the system properties, and EC2 metadata service not available");
        }
        throw new ApplicationFailureException("Unknown AWS region");
    }

    private AwsClients() {
    }

    private static volatile SdkHttpClient httpClient;
    private static final Object httpClientLock = new Object();

    private static SdkHttpClient getHttpClient() {
        SdkHttpClient c = httpClient;
        if (c != null) return httpClient;
        synchronized (httpClientLock) {
            c = httpClient;
            if (c == null) {
                httpClient = c = ApacheHttpClient.builder()
                    .maxConnections(PropertyStore.getInt("vf.aws.httpClient.maxConnections", 1000))
                    // The amount of time to wait when initially establishing a connection before giving up and timing out.
                    .connectionTimeout(Duration.ofMillis(PropertyStore.getLong("vf.aws.httpClient.connectionTimeout", 5000L)))
                    // The amount of time to wait for data to be transferred over an established, open connection before the connection is timed out.
                    .socketTimeout(Duration.ofMillis(PropertyStore.getLong("vf.aws.httpClient.socketTimeout", 10_000L)))
                    // The maximum amount of time that a connection should be allowed to remain open, regardless of usage frequency.
                    .connectionTimeToLive(Duration.ZERO)
                    // The maximum amount of time that a connection should be allowed to remain open while idle.
                    .connectionMaxIdleTime(Duration.ZERO)
                    .build();
            }
            return c;
        }
    }

    public static void closeHttpClient() {
        if (httpClient == null) return;
        synchronized (httpClientLock) {
            if (httpClient != null) {
                httpClient.close();
                httpClient = null;
            }
        }
    }


    private static boolean isThrottlingException(Throwable e) {
        if (e instanceof SdkServiceException s && s.isThrottlingException()) return true;

        String errmsg = e.getMessage();
        return (errmsg != null) &&
            (errmsg.contains("We currently do not have sufficient capacity") || errmsg.contains("Too many requests"));
    }

    private static BackoffStrategy backoffStrategy(long baseDelay, long maxDelay) {
        return maxDelay == 0 ? BackoffStrategy.retryImmediately() :
            maxDelay > baseDelay ? BackoffStrategy.exponentialDelay(Duration.ofMillis(baseDelay), Duration.ofMillis(maxDelay))
                : BackoffStrategy.fixedDelay(Duration.ofMillis(maxDelay));
    }

    private static StandardRetryStrategy.Builder retryStrategyBuilder() {
        return StandardRetryStrategy.builder().maxAttempts(RETRY_MAX_ATTEMPTS)
            .backoffStrategy(backoffStrategy(RETRY_BASE_DELAY, RETRY_MAX_DELAY))
            .treatAsThrottling(AwsClients::isThrottlingException)
            .throttlingBackoffStrategy(backoffStrategy(RETRY_BASE_THROTTLING_DELAY, RETRY_MAX_THROTTLING_DELAY));
    }

    public static S3ClientBuilder s3ClientBuilder() {
        // S3 retries through the shared standard strategy (throttling-aware) like every other client;
        // S3Support adds only response mapping (NoSuchKey/404/416), not its own retry loop. apiCallTimeout
        // bounds the whole call across retries; per-attempt stays bounded by API_CALL_ATTEMPT_TIMEOUT.
        return sdkClientBuilder(S3Client.builder(), cfg -> {
            if (S3_API_CALL_TIMEOUT > 0) {
                cfg.apiCallTimeout(Duration.ofMillis(S3_API_CALL_TIMEOUT));
            }
        });
    }

    /**
     * Builds a sync client with the shared credentials provider, region, and HTTP client, the
     * standard retry strategy (including throttling detection), and a per-attempt API call timeout
     * ({@link #API_CALL_ATTEMPT_TIMEOUT}). {@code overrideConfig}, if non-null, runs against the
     * {@link ClientOverrideConfiguration.Builder} after these defaults are set, so callers can add
     * settings (e.g. {@code apiCallTimeout}) or replace the retry strategy / attempt timeout.
     */
    public static <Client extends SdkClient,
        Builder extends AwsClientBuilder<Builder, Client> & SdkSyncClientBuilder<Builder, Client>>
    Builder sdkClientBuilder(Builder clientBuilder, Consumer<ClientOverrideConfiguration.Builder> overrideConfig) {
        var configBuilder = ClientOverrideConfiguration.builder()
            .retryStrategy(retryStrategyBuilder().build());
        if (API_CALL_ATTEMPT_TIMEOUT > 0) {
            configBuilder.apiCallAttemptTimeout(Duration.ofMillis(API_CALL_ATTEMPT_TIMEOUT));
        }
        if (overrideConfig != null) {
            overrideConfig.accept(configBuilder);
        }
        return clientBuilder
            .credentialsProvider(AwsCredentials.getProvider())
            .overrideConfiguration(configBuilder.build())
            .region(REGION)
            .httpClient(getHttpClient())
            .defaultsMode(DefaultsMode.IN_REGION);
    }

    public static <Client extends SdkClient,
        Builder extends AwsClientBuilder<Builder, Client> & SdkSyncClientBuilder<Builder, Client>>
    Builder sdkClientBuilder(Builder clientBuilder) {
        return sdkClientBuilder(clientBuilder, null);
    }

    /* ---------------------------------------------------------------------------------------------
     * Client accessors that switch between real AWS clients and the local (non-AWS) implementations
     * based on {@link LocalAws#ENABLED}. The local branch is taken first so that, in local mode,
     * the real-path configuration ({@link AwsCredentials}) is never touched -
     * its static initializer fails when AWS is not configured.
     * ------------------------------------------------------------------------------------------- */

    public static S3Client s3Client() {
        if (LocalAws.ENABLED) return new LocalS3Client();
        return s3ClientBuilder().build();
    }

    public static CloudWatchLogsClient cloudWatchLogsClient() {
        if (LocalAws.ENABLED) return new LocalCloudWatchLogsClient();
        return sdkClientBuilder(CloudWatchLogsClient.builder()).build();
    }

    public static SqsClient sqsClient() {
        if (LocalAws.ENABLED) return new LocalSqsClient();
        return sdkClientBuilder(SqsClient.builder()).build();
    }

    public static SnsClient snsClient() {
        if (LocalAws.ENABLED) return new LocalSnsClient();
        return sdkClientBuilder(SnsClient.builder()).build();
    }

    public static Ec2Client ec2Client() {
        if (LocalAws.ENABLED) return new LocalEc2Client();
        return sdkClientBuilder(Ec2Client.builder()).build();
    }

    public static IamClient iamClient() {
        if (LocalAws.ENABLED) return new LocalIamClient();
        return sdkClientBuilder(IamClient.builder())
            .defaultsMode(DefaultsMode.STANDARD).region(Region.AWS_GLOBAL).build();
    }

    public static EcrClient ecrClient() {
        if (LocalAws.ENABLED) return new LocalEcrClient();
        return sdkClientBuilder(EcrClient.builder()).build();
    }

    /**
     * Returns the real ECR Public client. In local mode {@code EcrPublicSupport} does not request
     * this client and builds its image catalog exclusively from Lambda {@code Runtime.knownValues()}.
     */
    public static EcrPublicClient ecrPublicClient() {
        return sdkClientBuilder(EcrPublicClient.builder())
            .defaultsMode(DefaultsMode.STANDARD).region(Region.US_EAST_1).build();
    }

    public static KmsClient kmsClient() {
        if (LocalAws.ENABLED) return new LocalKmsClient();
        return sdkClientBuilder(KmsClient.builder()).build();
    }

    public static LambdaClient lambdaClient() {
        return lambdaClient(null);
    }

    /**
     * Lambda client with optional per-caller builder customization (HTTP client, call timeouts,
     * retry strategy). In local mode the customizer is ignored and a {@link LocalLambdaClient} is
     * returned; in the real path the builder starts from the shared credentials/region/retry config
     * and the customizer is applied last (so it can override, e.g. the retry strategy).
     */
    public static LambdaClient lambdaClient(Consumer<LambdaClientBuilder> customizer) {
        if (LocalAws.ENABLED) return new LocalLambdaClient();
        LambdaClientBuilder builder = sdkClientBuilder(LambdaClient.builder());
        if (customizer != null) customizer.accept(builder);
        return builder.build();
    }

    /**
     * The standard retry strategy with an optional caller override, for callers that customize retry
     * behaviour on a specific client (e.g. {@code retryOnException}) via {@code overrideConfiguration}.
     */
    public static StandardRetryStrategy standardRetryStrategy(Consumer<StandardRetryStrategy.Builder> override) {
        StandardRetryStrategy.Builder builder = retryStrategyBuilder();
        if (override != null) override.accept(builder);
        return builder.build();
    }

    /**
     * Creates a holder for a single shared, JVM-lifetime AWS SDK client built lazily from
     * {@code factory} (typically one of the {@code xClient()} accessors above). This is the shared
     * implementation of the lazy-singleton + {@code shutdown()} lifecycle that the module's thin
     * support wrappers ({@link dev.olegz.vf.aws.s3.S3Support},
     * {@link dev.olegz.vf.aws.cloudwatch.CloudWatchLogsSupport}, etc.) used to each repeat.
     *
     * @param name    short client label used only in shutdown log messages (e.g. {@code "S3"})
     * @param factory builds the client on first use; invoked at most once per lazily-initialized instance
     */
    public static <C extends SdkClient> LazyClient<C> lazyClient(String name, Supplier<C> factory) {
        return new LazyClient<>(name, factory);
    }

    /**
     * A single shared AWS SDK client, built lazily on first {@link #get()} from the supplier passed to
     * {@link AwsClients#lazyClient(String, Supplier)} and held for the JVM lifetime. {@link #shutdown()}
     * closes it (e.g. between test contexts); the next {@link #get()} then rebuilds it. Thread-safe via
     * double-checked locking. In local mode the supplier returns a {@code Local*Client}, so the local
     * branch is handled by the supplier, not this holder.
     */
    public static final class LazyClient<C extends SdkClient> {
        private final String name;
        private final Supplier<C> factory;
        private volatile C client;

        private LazyClient(String name, Supplier<C> factory) {
            this.name = name;
            this.factory = factory;
        }

        /** The shared client, built on first call and reused until {@link #shutdown()}. */
        public C get() {
            C c = client;
            if (c == null) {
                synchronized (this) {
                    c = client;
                    if (c == null) {
                        client = c = factory.get();
                    }
                }
            }
            return c;
        }

        /** Idempotent: closes the shared client and its connection pool; safe to call more than once. */
        public synchronized void shutdown() {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    logger.warn("Error closing {} client", name, e);
                }
                client = null;
            }
        }
    }
}
