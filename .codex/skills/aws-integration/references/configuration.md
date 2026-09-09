# AWS configuration properties

All AWS configuration is read from `PropertyStore` under the `vf.aws.*` namespace, each with a
default in code — nothing AWS-related is hardcoded. Credentials are read via `PropertyStore.decrypt`
(encrypted at rest). Keys backed by `EnumProp` (e.g. `S3_MAX_TIMEOUT`) carry their default in the
`EnumProp` enum.

## Credentials & region

| Property | Default | Read in |
|---|---|---|
| `vf.aws.accessKeyId` | — (encrypted, required in real mode) | `AwsCredentials` |
| `vf.aws.secretAccessKey` | — (encrypted, required in real mode) | `AwsCredentials` |
| `vf.aws.region` | falls back to local default `us-east-1`, then EC2 metadata | `AwsClients.resolveRegion` |

## Shared HTTP client pool

| Property | Default | Read in |
|---|---|---|
| `vf.aws.httpClient.maxConnections` | `1000` | `AwsClients.getHttpClient` |
| `vf.aws.httpClient.connectionTimeout` | `5000` ms | `AwsClients.getHttpClient` |
| `vf.aws.httpClient.socketTimeout` | `10_000` ms | `AwsClients.getHttpClient` |

## Retry & timeouts

| Property | Default | Read in |
|---|---|---|
| `vf.aws.client.retry.maxAttempts` | `5` | `AwsClients` |
| `vf.aws.client.retry.baseDelay` | `100` ms | `AwsClients` |
| `vf.aws.client.retry.maxDelay` | `3000` ms | `AwsClients` |
| `vf.aws.client.retry.baseThrottlingDelay` | = `baseDelay` | `AwsClients` |
| `vf.aws.client.retry.maxThrottlingDelay` | = `maxDelay` | `AwsClients` |
| `vf.aws.client.apiCallAttemptTimeout` | `15_000` ms (per-attempt backstop) | `AwsClients` |
| `vf.aws.s3.client.maxTimeout` (`EnumProp.S3_MAX_TIMEOUT`) | `30_000` ms (overall S3 call budget) | `AwsClients.s3ClientBuilder` |

## ECR Public catalog

| Property | Default | Read in |
|---|---:|---|
| `vf.aws.ecrPublic.catalogRefreshInterval` | `86_400_000` ms (1 day) | `PythonImageCatalogLifecycle` |

## Lambda invoke client tuning

| Property | Default | Read in |
|---|---|---|
| `vf.aws.s3.client.maxConnections` | `500` | `LambdaFunctionInvoker` (async invoke client) |
| `vf.aws.s3.client.connectionTimeout` | `5000` ms | `LambdaFunctionInvoker` |

## Lambda VPC placement

| Property | Default | Read in |
|---|---|---|
| `vf.aws.lambda.vpc.name` | — | `LambdaAwsResources` |
| `vf.aws.lambda.vpc.securityGroup` | — | `LambdaAwsResources` |
| `vf.aws.lambda.vpc.subnets` | — (comma-separated; all VPC subnets if unset) | `LambdaAwsResources` |

## Buckets, logs, lambda resources

| Property | Default | Read in |
|---|---|---|
| `vf.aws.bucketName.application` | — | `Lambda` (S3 bucket for lambda artifacts) |
| `vf.aws.bucketName.logs` | — | `LambdaLogServiceImpl` (S3 bucket for log exports) |
| `vf.lambda.instanceLog.retentionDays` (`EnumProp.LAMBDA_LOG_RETENTION_DAYS`) | `60` | CloudWatch retention |
| `vf.lambda.build.deploymentTimeout` (`EnumProp.LAMBDA_DEPLOYMENT_TIMEOUT`) | `900` s | lambda deployment |
| `vf.lambda.systemLogLevel` | `INFO` | `LambdaFunctionDeployer` (Lambda logging config) |
| `vf.lambda.applicationLogLevel` | `WARN` | `LambdaFunctionDeployer` |

## Local mode (offline, file-backed)

| Property | Default                 | Read in |
|---|-------------------------|---|
| `vf.aws.local` | `false`                 | `LocalAws.ENABLED` — master switch for file-backed mocks |
| `vf.aws.local.root` | `<home>/aws`            | local data root, partitioned by region |
| `vf.aws.local.docker.command` | `docker`                | `LocalDocker` |
| `vf.aws.local.ecr.repoPrefix` | `vf-local`           | local ECR |
| `vf.aws.local.lambda.portRange` | `9000-9100`             | local Lambda (RIE) |
| `vf.aws.local.lambda.rie` | —                       | Lambda Runtime Interface Emulator path |
| `vf.aws.local.lambda.handler` | `lambda.lambda_handler` | local Lambda |
| `vf.aws.local.lambda.entrypoint` | `/lambda-entrypoint.sh` | local Lambda |

In local mode, `LocalAws.ACCOUNT_ID` is the synthetic id `000000000000` used in generated ARNs,
and `AwsClients.REGION` short-circuits to `us-east-1` unless `vf.aws.region` is set. When
verifying or testing AWS code paths without real AWS access, set `vf.aws.local=true`.

> Note: exact default values and a few local-mode keys are pulled from code that evolves; confirm
> against `AwsClients`, `LocalAws`, `EnumProp`, and the relevant `*Support` / worker classes
> before relying on a specific number.
