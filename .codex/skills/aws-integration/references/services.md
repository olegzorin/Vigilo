# Per-service detail and usage

Each wrapped service is a thin static facade over the shared client. Below: the facade, its key
methods, caching, exception handling, and real call sites. Paths are relative to the project root.

## S3 — `aws/.../s3/S3Support.java`

Object storage. Holds a `LazyClient<S3Client>` (built via `AwsClients::s3Client`). All operations
run through a private `execute(operation, ignoreNotFound, errorMessage)` helper that maps the
terminal outcome — it does **not** retry (the shared `StandardRetryStrategy` already does):

- `NoSuchKeyException`, S3 `404`, S3 `416` → `null` when `ignoreNotFound` is true, else
  the result of `AwsExceptions.wrapAwsException`
- `ApiCallTimeoutException` / `ApiCallAttemptTimeoutException` → `ExternalConnectionException`
- `SdkClientException` caused by `SocketTimeoutException`/`UnknownHostException` → `ExternalConnectionException`
- any other `SdkServiceException` → `ExternalException`; other `SdkException` →
  `ApplicationFailureException`

Methods:

- `createObject(bucket, objectId, byte[] data, contentType, boolean encrypted)` — `PutObject` with
  a Content-MD5 integrity header; `encrypted` sets `ServerSideEncryption.AES256`.
- `getData(bucket, objectId)` → `byte[]` or `null` if absent (uses `getObjectAsBytes`).
- `deleteObjects(bucket, List<String> objectIds, Collection<String> errorObjectIds)` — batches in
  groups of `MAX_DELETE_BATCH_SIZE` (1000); failed keys are added to `errorObjectIds`.

Call sites: upload at `api/.../lambda/deployment/LambdaDeployAction.java`; delete at
`worker/.../service/LambdaDeploymentService.java`.

## S3 presigned URLs — `aws/.../s3/S3Presigning.java`

Holds a shared, lazily-built `S3Presigner` (its own double-checked-locking field, since presigners
aren't `SdkClient`s and aren't part of `LazyClient`). **Branches on `LocalAws.ENABLED` first**: in
local mode it returns an `http://` URL served by `LocalS3HttpServer` (reachable from inside a build
container via `host.docker.internal`), so presigned-URL flows work offline.

- `makeS3WritePresignedUrl(bucket, objectId, expiration, contentType, boolean encrypted)` → `URL`
- `makeS3ReadPresignedUrl(bucket, objectId, expiration, contentDisposition)` → `URL`

`ENCRYPTION_HEADER` is a ready-made `Map` for the SSE header callers must send with an encrypted
PUT. The current call site is `core/.../domain/lambdabuild/BuildConfig.java`, which creates a source
archive read URL for the lambda-image build.

## SQS — `aws/.../sqs/SqsSupport.java`

Queue provisioning and message transport. Holds a `LazyClient<SqsClient>`. All logical queue names
are normalized with `AwsResourceNames.prefixed`. Queue URLs and ARNs are cached separately in
`ConcurrentHashMap`s keyed by the normalized resource name.

- `makeSqsQueue(queueName, delay, messageRetention, visibilityTimeout)` → queue ARN.
  Resolves or creates the queue, reconciles the requested attributes with `setQueueAttributes`,
  then reads and caches `QUEUE_ARN`.
- `makeSqsQueue(..., deadLetterQueueArn, maxReceiveCount)` → queue ARN, additionally configuring
  the SQS redrive policy. Existing queues are reconciled as well as newly-created queues.
- `sendMessage(queueName, body, delay, messageRetention, visibilityTimeout)` provisions/resolves the
  queue and sends one message.
- `receiveMessages(queueName, maxMessages, waitTimeSeconds, visibilityTimeout)` → `List<SqsMessage>`;
  requests the SQS sent timestamp for each message.
- `deleteMessage(queueName, receiptHandle)` acknowledges a successfully-processed message by
  deleting it from SQS.

`worker/.../service/LambdaResultQueueProvisioner.java` provisions `dev-botlab-lambda-results` and
`dev-botlab-lambda-results-dlq`, validates their retention/visibility/redrive configuration, and
returns the main queue ARN. Worker startup provisions the pair before registering the SQS listener;
`LambdaFunctionDeployer` also calls the provisioner and wires the main queue as both the success and
failure destination of the qualified ASYNC Lambda version. Message transport call sites are
`messaging/.../providers/sqs/SqsMessageProducer.java` and `SqsConsumer.java`.

## ECR — `aws/.../ecr/EcrSupport.java`

Container image registry. Holds a `LazyClient<EcrClient>`.

- `getOrCreateRepository(repoName)` → repository URI. `describeRepositories`; on
  `RepositoryNotFoundException` creates it (IMMUTABLE tags, scan-on-push off, AES256 encryption).
  Uses `IamSupport.getAccountId()` as the registry id.
- `imageExists(repoName, imageTag)` → boolean; `ImageNotFoundException` → `false`.
- `deleteImages(repoName, List<String> imageTags, Collection<String> errorsCollector)` —
  `batchDeleteImage`; collects failed tags (ignoring `IMAGE_NOT_FOUND`).

Call sites: `worker/.../LambdaAwsResources.java`, `.../service/LambdaDeploymentService.java`.

## ECR Public — `aws/.../ecr/EcrPublicSupport.java`

Read-only discovery of AWS Lambda Python base images. Holds a lazy `EcrPublicClient` for obtaining
an authorization token and a separate shared JDK `HttpClient` for the public registry tags API.
The published catalog is an immutable, atomically-replaced snapshot.

- `getPythonImageTags()` → sorted, deduplicated `<major>.<minor>-<architecture>` tags.
- `getPythonImageUri(imageTag)` → canonical `public.ecr.aws/lambda/python:<tag>` URI after
  validating the tag.
- `refreshPythonImageTags()` fetches all registry pages and replaces the snapshot only after a
  successful refresh. In local mode it uses tags derived from Lambda `Runtime.knownValues()` and
  does not request AWS credentials or the ECR Public client.

Call sites: `api/.../lambda/deployment/LambdaDeployAction.java`,
`api/.../lambda/deployment/PythonImageCatalogLifecycle.java`, and
`core/.../domain/lambdabuild/BuildConfig.java`.

## SNS — `aws/.../sns/SnsSupport.java`

Topic provisioning, publishing, and filtered subscriptions. Holds a `LazyClient<SnsClient>` and
caches topic ARNs by their `AwsResourceNames.prefixed` names.

- `makeSnsTopic(topicName)` → topic ARN; SNS `createTopic` supplies idempotent provisioning.
- `publish(...)` → message id, with overloads for an optional subject and numeric message
  attributes.
- `subscribeWithNumericFilter(...)` → subscription ARN with an SNS numeric filter policy.

Call site: `core/.../service/notification/SnsNotificationService.java`.

## CloudWatch Logs — `aws/.../cloudwatch/CloudWatchLogsSupport.java`

Log group/stream lifecycle, event writing, S3 export. Holds a `LazyClient<CloudWatchLogsClient>`.

- `createLogGroup(logGroupName, retentionDays)` — creates (STANDARD class), ignores
  `ResourceAlreadyExistsException`, then sets a clamped retention policy.
- `deleteLogGroup(logGroupName)`
- `writeLogEvents(logGroupName, logStreamName, List<InputLogEvent>, List<String> warnings)` —
  `putLogEvents`; rejected events become warnings; `ResourceNotFoundException` logged and ignored.
- `createExportTask(taskName, logGroupName, logStreamName, startDate, endDate, destinationBucket, destinationPrefix)`
  → taskId; `ResourceNotFoundException` → `ObjectNotFoundException`.

Call sites: `core/.../service/lambda/LambdaLogServiceImpl.java`.

## KMS — `aws/.../kms/KmsSupport.java`

Envelope encryption (KEK/DEK). **Holds an eager `static final KmsClient`** (not a `LazyClient`) —
the historical exception; prefer `LazyClient` for new services. Exposes a `DataKey(plaintext,
wrapped)` record.

- `putKek(description)` → keyId (creates a symmetric ENCRYPT_DECRYPT key)
- `putDek(kekId)` → `DataKey` (`generateDataKey`, AES_256)
- `getDek(kekId, byte[] wrappedDek)` → plaintext `byte[]` (`decrypt`)

## IAM — `aws/.../iam/IamSupport.java`

Role/account lookups. Builds a **fresh `IamClient` per call** via `AwsClients.iamClient()` inside a
try-with-resources, but **caches the results** in a `ConcurrentHashMap` for the JVM lifetime (these
values don't change). Uses `Region.AWS_GLOBAL`.

- `getRoleArn(roleName)` → ARN; `NoSuchEntityException` → `ApplicationFailureException`. Cache
  key `role:<name>`.
- `getAccountId()` → account id parsed from the caller ARN. Cache key `accountId`.

## Lambda — no facade; `AwsClients.lambdaClient(...)`

Lambda has no `*Support` class because callers need different tuning per use. All clients are still
built through `AwsClients`. In `worker/.../service/`:

- `LambdaFunctionInvoker` — synchronous `REQUEST_RESPONSE` invoke uses a cached client with a dedicated
  Apache HTTP pool, 900-second call/attempt/socket timeouts, and no retries. The legacy
  `vf.aws.s3.client.maxConnections` and `vf.aws.s3.client.connectionTimeout` properties tune this
  synchronous client. Asynchronous `EVENT` invoke uses a short-lived default client in
  try-with-resources.
- `LambdaFunctionDeployer` — `updateFunctionCode` / `updateFunctionConfiguration`, `createFunction`
  (on `ResourceNotFoundException`, image package type), `publishVersion`, and qualified ASYNC
  destination configuration; short-lived clients in try-with-resources. VPC configuration is
  currently disabled in the create request. Errors are wrapped via `AwsExceptions.wrapAwsException`.

## EC2 — no facade; `AwsClients.ec2Client()`

`worker/.../LambdaAwsResources.java` can resolve VPC / security group / subnet ids for Lambda placement
inside a `try (var ec2Client = AwsClients.ec2Client())` block. Resolved ids are cached in a
`staticRefs` map for the JVM lifetime. It uses `describeVpcs` / `describeSecurityGroups` /
`describeSubnets` with a VPC filter and `CollectionOps.findAny` / `CollectionOps.map` (not
Streams). The current `LambdaFunctionDeployer` create request does not call this VPC path.
