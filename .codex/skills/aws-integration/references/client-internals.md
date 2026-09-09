# AwsClients internals

Everything client-construction-related lives in
`aws/src/main/java/dev/olegz/vf/aws/client/AwsClients.java`. It is a `final` class with a
private constructor — pure static factory. This is the single place every AWS SDK client in the
project is built, so all clients share the same credentials, region, retry policy, timeouts, and
HTTP connection pool.

## Table of contents

- [The shared builder: `sdkClientBuilder`](#the-shared-builder-sdkclientbuilder)
- [Shared HTTP connection pool](#shared-http-connection-pool)
- [Retry strategy and throttling](#retry-strategy-and-throttling)
- [Timeouts](#timeouts)
- [Region resolution](#region-resolution)
- [Credentials](#credentials)
- [`LazyClient`: shared lazy lifecycle](#lazyclient-shared-lazy-lifecycle)
- [Per-service factories and local mode](#per-service-factories-and-local-mode)
- [Per-caller customization hooks](#per-caller-customization-hooks)
- [Spring lifecycle bridge](#spring-lifecycle-bridge)

## The shared builder: `sdkClientBuilder`

Every factory routes through `sdkClientBuilder(builder)` (or the overload taking a
`Consumer<ClientOverrideConfiguration.Builder>`), which applies the shared cross-cutting config:

```java
public static <Client extends SdkClient,
    Builder extends AwsClientBuilder<Builder, Client> & SdkSyncClientBuilder<Builder, Client>>
Builder sdkClientBuilder(Builder clientBuilder, Consumer<ClientOverrideConfiguration.Builder> overrideConfig) {
    var configBuilder = ClientOverrideConfiguration.builder()
        .retryStrategy(retryStrategyBuilder().build());
    if (API_CALL_ATTEMPT_TIMEOUT > 0) {
        configBuilder.apiCallAttemptTimeout(Duration.ofMillis(API_CALL_ATTEMPT_TIMEOUT));
    }
    if (overrideConfig != null) {
        overrideConfig.accept(configBuilder);          // caller overrides applied last
    }
    return clientBuilder
        .credentialsProvider(AwsCredentials.getProvider())
        .overrideConfiguration(configBuilder.build())
        .region(REGION)
        .httpClient(getHttpClient())                   // shared pool
        .defaultsMode(DefaultsMode.IN_REGION);
}
```

`DefaultsMode.IN_REGION` opts into AWS SDK "smart configuration defaults" tuned for in-region
calls. The `overrideConfig` consumer runs **after** the defaults, so a caller can add or replace
settings (e.g. add an `apiCallTimeout`, swap the retry strategy). This is how `s3ClientBuilder()`
adds the overall S3 call-budget timeout on top of the per-attempt timeout.

## Shared HTTP connection pool

A single `ApacheHttpClient` is built once (double-checked locking on a `volatile` field) and passed
to every client. Sharing one pool caps total sockets and lets connections be reused across
services instead of each client opening its own pool.

```java
httpClient = ApacheHttpClient.builder()
    .maxConnections(PropertyStore.getInt("vf.aws.httpClient.maxConnections", 1000))
    .connectionTimeout(Duration.ofMillis(PropertyStore.getLong("vf.aws.httpClient.connectionTimeout", 5000L)))
    .socketTimeout(Duration.ofMillis(PropertyStore.getLong("vf.aws.httpClient.socketTimeout", 10_000L)))
    .connectionTimeToLive(Duration.ZERO)       // no forced TTL
    .connectionMaxIdleTime(Duration.ZERO)      // no idle eviction
    .build();
```

`closeHttpClient()` closes and nulls the pool; it's called by `AwsClientLifecycle` on context
shutdown, after the per-service clients are closed. Because the pool is shared, **never close it
from a `*Support` class** — only `AwsClientLifecycle` owns that.

## Retry strategy and throttling

Retries use SDK v2's `StandardRetryStrategy`, configured once and reused. Support classes rely on
it entirely — they do not loop.

```java
private static StandardRetryStrategy.Builder retryStrategyBuilder() {
    return StandardRetryStrategy.builder().maxAttempts(RETRY_MAX_ATTEMPTS)        // default 5
        .backoffStrategy(backoffStrategy(RETRY_BASE_DELAY, RETRY_MAX_DELAY))      // 100ms → 3000ms
        .treatAsThrottling(AwsClients::isThrottlingException)
        .throttlingBackoffStrategy(backoffStrategy(RETRY_BASE_THROTTLING_DELAY, RETRY_MAX_THROTTLING_DELAY));
}
```

Throttling gets its own backoff and a custom detector that catches both real throttling exceptions
and capacity messages the SDK doesn't classify:

```java
private static boolean isThrottlingException(Throwable e) {
    if (e instanceof SdkServiceException s && s.isThrottlingException()) return true;
    String errmsg = e.getMessage();
    return (errmsg != null) &&
        (errmsg.contains("We currently do not have sufficient capacity") || errmsg.contains("Too many requests"));
}
```

`backoffStrategy(base, max)` picks the shape from the values: `0` max → retry immediately,
`max > base` → exponential, otherwise → fixed delay. So retry behavior is fully property-driven.

## Timeouts

Two distinct timeouts, deliberately separated so a single stuck attempt doesn't kill the whole
retry budget:

- **`API_CALL_ATTEMPT_TIMEOUT`** (`vf.aws.client.apiCallAttemptTimeout`, default 15_000ms) —
  per-attempt backstop, applied to all retrying clients. Sized above the HTTP connect (5s) +
  socket (10s) timeouts so it only fires on a genuinely stuck attempt.
- **`S3_API_CALL_TIMEOUT`** (`EnumProp.S3_MAX_TIMEOUT` = `vf.aws.s3.client.maxTimeout`) —
  overall call budget across all retries, applied by `s3ClientBuilder()` only.

## Region resolution

`AwsClients.REGION` is resolved once at class load, in priority order:

```java
private static Region resolveRegion() {
    String propsValue = PropertyStore.getString("vf.aws.region");
    if (propsValue != null) return Region.of(propsValue);          // 1. explicit property
    if (LocalAws.ENABLED) return Region.of("us-east-1");           // 2. local-mode default
    try {
        String ec2value = EC2MetadataUtils.getEC2InstanceRegion(); // 3. EC2 instance metadata
        if (ec2value != null) return Region.of(ec2value);
    } catch (SdkException e) {
        throw new ExecutionException("AWS Region not configured ... and EC2 metadata service not available");
    }
    throw new ExecutionException("Unknown AWS region");
}
```

IAM is the exception: `iamClient()` overrides to `Region.AWS_GLOBAL` and `DefaultsMode.STANDARD`,
because IAM is a global, non-regional service.

## Credentials

`AwsCredentials.getProvider()` (`aws/.../config/AwsCredentials.java`) lazily decrypts
`vf.aws.accessKeyId` / `vf.aws.secretAccessKey` from the encrypted property store and wraps
them in a `StaticCredentialsProvider`. It's lazy and double-checked-locked **on purpose**: in local
mode there are no credentials, and resolution must never be triggered. This is why
`AwsClients.xClient()` takes the `LocalAws.ENABLED` branch *before* calling `sdkClientBuilder`
(which touches `AwsCredentials`), and why `LocalAws` is documented to never reference
`AwsCredentials`.

## `LazyClient`: shared lazy lifecycle

`AwsClients.lazyClient(name, factory)` returns a `LazyClient<C>` — the shared implementation of the
"one lazily-built, JVM-lifetime client with idempotent shutdown" pattern that each support wrapper
used to repeat by hand.

```java
public static final class LazyClient<C extends SdkClient> {
    private final String name;
    private final Supplier<C> factory;
    private volatile C client;

    public C get() {                       // double-checked locking; builds at most once
        C c = client;
        if (c == null) {
            synchronized (this) {
                c = client;
                if (c == null) client = c = factory.get();
            }
        }
        return c;
    }

    public synchronized void shutdown() {  // idempotent; next get() rebuilds
        if (client != null) {
            try { client.close(); } catch (Exception e) { logger.warn("Error closing {} client", name, e); }
            client = null;
        }
    }
}
```

The `factory` is typically a method reference to an `AwsClients` accessor (`AwsClients::s3Client`),
so the local-vs-real switch lives in the factory, not in `LazyClient`. After `shutdown()`, the next
`get()` rebuilds — which is what makes it safe to close clients between test contexts.

## Per-service factories and local mode

Each `xClient()` accessor takes the local branch first, then builds the real client through the
shared builder:

```java
public static S3Client s3Client() {
    if (LocalAws.ENABLED) return new LocalS3Client();
    return s3ClientBuilder().build();
}
```

Factories exist for: `s3Client`, `cloudWatchLogsClient`, `sqsClient`, `ec2Client`, `iamClient`,
`ecrClient`, `kmsClient`, `lambdaClient` (+ customizer overload). Each has a matching
`Local*Client` in `aws/.../local/`.

## Per-caller customization hooks

Two hooks let a caller deviate from the shared defaults without hand-rolling a builder:

- `lambdaClient(Consumer<LambdaClientBuilder> customizer)` — starts from the shared
  credentials/region/retry config; the customizer is applied last so it can override (e.g. HTTP
  limits, a no-retry strategy for async `EVENT` invokes).
- `standardRetryStrategy(Consumer<StandardRetryStrategy.Builder> override)` — the standard strategy
  with a caller tweak, for callers that set their own retry behavior on a specific client via
  `overrideConfiguration`.

## Spring lifecycle bridge

`core/src/main/java/dev/olegz/vf/core/config/AwsClientLifecycle.java` is the only Spring
seam. It's registered as a `@Bean` in `CoreConfig`, so every app importing the core config
(worker, api, scheduler) closes clients on shutdown:

```java
@PreDestroy
public void shutdown() {
    S3Support.shutdown();
    SqsSupport.shutdown();
    CloudWatchLogsSupport.shutdown();
    EcrSupport.shutdown();
    AwsClients.closeHttpClient();          // close per-service clients first, then the shared pool
}
```

When you add a new `*Support` class holding a `LazyClient`, add its `shutdown()` here. (KMS and IAM
aren't listed: `IamSupport` uses short-lived per-call clients, and `KmsSupport` currently holds an
eager client — prefer the `LazyClient` + register-here pattern for anything new.)
