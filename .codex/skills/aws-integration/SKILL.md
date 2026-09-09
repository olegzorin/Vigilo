---
name: aws-integration
description: >-
  Conventions and architecture for working with AWS (SDK v2) in this codebase. Use this
  whenever you touch AWS code in this project — adding or modifying an AWS service wrapper,
  adding an operation to an S3/SQS/ECR/CloudWatch/KMS/IAM/Lambda/EC2 helper, building or
  configuring AWS SDK clients, handling AWS credentials/region/retries/timeouts, uploading or
  downloading S3 objects, generating presigned URLs, sending SQS messages, invoking Lambda, or
  debugging AWS client lifecycle and local-mode (vf.aws.local) behavior. Apply it even when
  the request just mentions S3, SQS, ECR, CloudWatch Logs, KMS, IAM, Lambda, buckets, queues, or
  the AwsClients / *Support classes without naming the conventions explicitly.
---

# Working with AWS in this project

All AWS access in this codebase goes through the **`aws` Maven module**
(`dev.olegz.vf.aws`), which wraps **AWS SDK v2**. The module is small and opinionated:
a single client factory, a handful of thin static `*Support` facades, file-backed local mocks,
and one exception translator. Follow these conventions so AWS code stays uniform and the
guarantees below (shared connection pool, central retry policy, clean shutdown, offline local
mode) keep holding.

If something here disagrees with what you see in the code, trust the code and tell the user — the
module is actively evolving (see recent commits around `AwsClients.lazyClient`, shared HTTP pool,
SDK retry strategy).

## The architecture in one picture

```
caller (core / worker / api / scheduler)
   │  calls public static methods only
   ▼
S3Support / SqsSupport / SnsSupport / EcrSupport / EcrPublicSupport / CloudWatchLogsSupport
   │  usually hold ONE shared, lazily-built SDK client (AwsClients.LazyClient<C>)
   │  exceptions: KmsSupport is eager; IamSupport is per-call; S3Presigning owns its presigner
   ▼
AwsClients  ── builds every client via sdkClientBuilder():
   ├─ AwsCredentials.getProvider()   (encrypted static creds, lazy)
   ├─ AwsClients.REGION              (resolved once)
   ├─ StandardRetryStrategy          (throttling-aware, exponential backoff)
   ├─ ClientOverrideConfiguration    (per-attempt + per-call timeouts)
   └─ shared ApacheHttpClient pool   (one pool across all clients)
        │
        └─ if vf.aws.local=true → returns Local*Client (file-backed mock) instead
```

Spring touches AWS in exactly one place: `core` registers `AwsClientLifecycle`
(`core/.../config/AwsClientLifecycle.java`), whose `@PreDestroy` calls each support class's
`shutdown()` and `AwsClients.closeHttpClient()`. The `aws` module itself is **Spring-free**.

## Non-negotiable conventions

These are the rules that keep the module coherent. Each has a reason — understand the reason and
you'll know when an exception is genuinely warranted.

1. **Callers go through a `*Support` facade, never the SDK directly.** No code outside the `aws`
   module should `import software.amazon.awssdk.*` or call `S3Client.builder()`. The facades own
   the client lifecycle, retry behavior, exception translation, and local-mode switch; bypassing
   them loses all of that. (Lambda and EC2 are the documented exceptions — see below.)

2. **Build every client through `AwsClients`, never `XClient.builder()` ad hoc.** A client built
   outside `AwsClients.sdkClientBuilder(...)` skips the shared credentials, region, retry strategy,
   timeouts, **and** the shared HTTP connection pool — meaning it leaks its own pool of up to 1000
   connections. If you need a service that isn't wrapped yet, add a factory to `AwsClients` (recipe
   below), don't hand-roll a builder at the call site.

3. **One shared, lazily-built, JVM-lifetime client per service.** Hold it in an
   `AwsClients.LazyClient<C>`, not a fresh client per call. SDK clients are heavyweight (they own
   thread pools and connection state) and are designed to be long-lived and shared. The
   `LazyClient` builds on first use (so local mode and missing credentials don't blow up at class
   load) and is closed by `shutdown()`.

4. **Don't write manual retry loops.** Transient failures (throttling, 5xx, connection resets,
   per-attempt timeouts) are retried by the centrally-configured `StandardRetryStrategy`. A
   `*Support` method runs the call once and only **maps the terminal outcome** — translating
   "not found" to `null`/`false`, connectivity failures to `ExternalConnectionException`, and
   everything else via `AwsExceptions`. Adding your own loop double-retries and fights the SDK.

5. **Translate SDK exceptions with `AwsExceptions`; never let them escape the module.** Use
   `AwsExceptions.wrapAwsException(e, msg)` to convert to the app's `ExternalException` /
   `ApplicationFailureException`, or `logAwsExceptionAsError/Warning(logger, e, msg)` when you swallow.
   Map service-specific "absent" exceptions (`NoSuchKeyException`, `RepositoryNotFoundException`,
   `ImageNotFoundException`, `ResourceAlreadyExistsException`, `ResourceNotFoundException`,
   `QueueDoesNotExistException`) to sensible domain behavior rather than rethrowing raw.

6. **Everything must work in local mode (`vf.aws.local=true`).** The `AwsClients.xClient()`
   factories return file-backed `Local*Client` mocks when local mode is on. Any new helper that
   builds an AWS object directly (like `S3Presigning`'s presigner) **must branch on
   `LocalAws.ENABLED` first** and never reference `AwsCredentials` on the local path — its static
   initializer throws when AWS isn't configured, which is exactly the local case.

7. **Register new long-lived clients for shutdown.** When you add a `*Support` class that holds a
   `LazyClient`, add its `shutdown()` call to `AwsClientLifecycle.shutdown()` in `core` so the
   client and its connections are released cleanly on context shutdown.

8. **Read config from `PropertyStore` under the `vf.aws.*` namespace, with a default.** Don't
   hardcode timeouts, sizes, bucket names, or region. See `references/configuration.md`.

9. **Follow the project-wide Java rules here too:** the `aws` module depends on `common` only
   (not `core`/`aws` consumers) and stays Spring-free. Prefer `CollectionOps` to the Java Stream
   API for a single collection operation when the input collection may be `null`; use streams
   when chaining multiple operations makes the processing pipeline clearer.

## The canonical `*Support` class

Every wrapped service follows this shape. Copy it when wrapping a new service:

```java
public final class XSupport {
    private XSupport() {}

    private static final Logger logger = LoggerFactory.getLogger(XSupport.class);

    // One shared client, built lazily, held for the JVM lifetime, rebuilt if shutdown() closed it.
    private static final AwsClients.LazyClient<XClient> CLIENT =
        AwsClients.lazyClient("X", AwsClients::xClient);

    private static XClient client() {
        return CLIENT.get();
    }

    /** Idempotent: closes the shared client and its connection pool; safe to call more than once. */
    public static void shutdown() {
        CLIENT.shutdown();
    }

    public static SomeResult doThing(String arg) {
        try {
            return client().someOperation(r -> r.field(arg)).result();
        } catch (SomeNotFoundException e) {
            return null;                       // map "absent" to domain behavior
        } catch (Exception e) {
            throw AwsExceptions.wrapAwsException(e, "Exception in doThing arg=" + arg);
        }
    }
}
```

Stateless idempotent lookups (queue ARNs, account id, role ARNs) additionally cache results in a
`ConcurrentHashMap` via `computeIfAbsent` — see `SqsSupport` and `IamSupport`.

## Common tasks

### Add an operation to an existing service

Add a `public static` method to the relevant `*Support` class that calls `client().<op>(...)`,
wrap it in try/catch with `AwsExceptions`, and **do not** add a retry loop. Match the existing
methods' logging and "not found" handling. That's the whole change.

### Wrap a new AWS service

1. **Dependency:** add the SDK artifact to `aws/pom.xml` with `<version>${aws-java-sdk-2-version}</version>`.
2. **Local mock:** add a `Local<Service>Client` under `aws/.../local/` implementing the SDK client
   interface (back it with the local file store / a stub) so local mode keeps working.
3. **Factory:** add `xClient()` to `AwsClients`, taking the local branch first:
   ```java
   public static XClient xClient() {
       if (LocalAws.ENABLED) return new LocalXClient();
       return sdkClientBuilder(XClient.builder()).build();
   }
   ```
4. **Facade:** create `XSupport` from the template above.
5. **Shutdown:** register `XSupport.shutdown()` in `AwsClientLifecycle.shutdown()`.
6. **Config:** add any `vf.aws.x.*` keys to `PropertyStore` lookups with defaults.

### Per-caller client customization (timeouts / retry / invocation mode)

A few callers legitimately need their own client tuning. For example, synchronous Lambda
`REQUEST_RESPONSE` invocation uses a dedicated HTTP pool, extended timeouts, and no retries, while
deployment uses the central retry strategy with a custom exception predicate. Use the customizer
hooks on `AwsClients` rather than building a raw client — they start from the shared config and let
you override just what you need:

```java
LambdaClient client = AwsClients.lambdaClient(b -> b.overrideConfiguration(...));   // customizer applied last
StandardRetryStrategy noRetry = AwsClients.standardRetryStrategy(b -> b.maxAttempts(1));
```

Deployment and asynchronous-invocation clients are created per use and closed with
try-with-resources; the specially-tuned synchronous invocation client is cached and explicitly
closed by its owning service. EC2 also uses per-operation clients. These are exceptions to "one
shared client", but all are still built through `AwsClients`, so credentials and region remain
centralized. A caller that overrides `httpClient(...)` owns that dedicated pool; callers that do
not override it use the shared pool.

## Service catalog (quick reference)

| Service | Facade / entry point | Notes |
|---|---|---|
| S3 objects | `S3Support` | `createObject`, `getData`, `deleteObjects`; maps 404/416/NoSuchKey → null |
| S3 presigned URLs | `S3Presigning` | shared `S3Presigner`; local mode serves via `LocalS3HttpServer` |
| SQS | `SqsSupport` | queue/DLQ provisioning with attribute reconciliation; send/receive/delete |
| SNS | `SnsSupport` | topic provisioning, publish, numeric-filter subscriptions |
| ECR | `EcrSupport` | `getOrCreateRepository`, `imageExists`, `deleteImages` |
| ECR Public | `EcrPublicSupport` | cached Lambda Python image catalog; local runtime-derived fallback |
| CloudWatch Logs | `CloudWatchLogsSupport` | log groups/streams, `writeLogEvents`, `createExportTask` |
| KMS | `KmsSupport` | envelope encryption: `putKek`, `putDek`, `getDek` (eager client) |
| IAM | `IamSupport` | `getRoleArn`, `getAccountId`; per-call client, results cached; `Region.AWS_GLOBAL` |
| Lambda | `AwsClients.lambdaClient(customizer)` | no facade; per-invocation clients (see worker) |
| EC2 | `AwsClients.ec2Client()` | no facade; per-operation block in `LambdaAwsResources` |

## Where to read more

- **`references/client-internals.md`** — `AwsClients` internals: the shared HTTP pool, retry
  strategy and throttling detection, region/credentials resolution, `LazyClient` mechanics, and
  the Spring lifecycle bridge. Read this when changing client construction, timeouts, retries, or
  connection pooling.
- **`references/services.md`** — per-service detail: method signatures, caching, exception
  handling, and real call sites across the codebase. Read this when adding operations or finding
  usage examples.
- **`references/configuration.md`** — every `vf.aws.*` property, its default, and where it's
  read. Read this when configuring an environment or adding a tunable.
