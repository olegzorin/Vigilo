# Vigilo Framework architecture and processes

## System shape

VF is a Java 25 multi-module Maven project. Spring Boot supplies the executable applications,
Spring manages services and transactions, MyBatis implements persistence, Kafka carries durable
application streams, and AWS SDK v2 integrations provide production infrastructure services.

The normal server deployment contains two long-running JVMs:

```text
clients and lambdas
      |
      v
+-------------------+       +-------------------+
| API :8086         |       | Worker :8082      |
| REST/auth/report  |       | consumers/jobs    |
+---------+---------+       +---------+---------+
          |                           |
          +------------+--------------+
                       |
              +--------+--------+
              |                 |
              v                 v
           PostgreSQL        Kafka
              |                 |
              +--------+--------+
                       |
                       v
             AWS services or local
             file/Docker substitutes
```

The API handles synchronous client boundaries and produces asynchronous work. The worker consumes
that work, invokes lambdas, runs scheduled jobs, and dispatches durable outboxes. Both processes use
the same database and Kafka cluster. The report REST endpoints are embedded in the API; the report
module can also be started independently when a report-only service is wanted.

## Module boundaries

```text
foundation <------------------------------- mcp
    |
    +--> aws ---------+
    +--> messaging ---+--> core --> api
    +--> registry ----+      |       ^
           |                 +--> worker
           +--> report --------------+
```

| Module | Responsibility |
| --- | --- |
| `foundation` | Properties, environment paths, logging, JSON mapping, exceptions, utilities, and concurrency support. |
| `aws` | Central AWS SDK v2 client lifecycle and S3, SQS, SNS, ECR, CloudWatch, KMS, IAM, Lambda, and EC2 integrations, including local substitutes. |
| `messaging` | Broker-neutral producer/listener contracts plus Kafka, SQS, Artemis, and in-memory implementations. It owns `kafka-clients`. |
| `registry` | Organizations, hierarchy, users, locations, devices, authentication, authorization, and their MyBatis persistence. |
| `core` | Lambda definitions, versions, assignments, deployment, execution state, variables, alerts, caching, integration services, and database outboxes. |
| `report` | Report definitions, registration, persistence, SQL execution, output storage, and report REST configuration. |
| `worker` | Kafka/SQS listeners, lambda execution, deployment work, cron jobs, retries, outbox dispatch, and scheduled reports. |
| `api` | Public Spring MVC endpoints, user and lambda authentication boundaries, and the embedded report API. |
| `mcp` | A separate Model Context Protocol process for controlled access to the local PostgreSQL database. It is not part of the API/worker runtime. |

Dependencies point inward toward reusable modules. `registry` does not depend on AWS or messaging;
`report` does not depend on `core` or `worker`; and executable applications assemble the required
configuration at their outer boundary.

## Runtime configuration

Each application calls `PropertyStore.start()` before Spring starts. `VF_HOME` identifies the
runtime home and properties are loaded from:

```text
$VF_HOME/config/properties/*.properties
```

The directory is watched for changes. Programmatic overrides win over file values. `VF_LOG_HOME`
can override the default `$VF_HOME/logs`, while `VF_KEK` supplies the stable base64-encoded
key-encryption key.

Spring's `application.properties` remains inside each executable JAR and controls HTTP and
management ports. Operational `vf.*` and `jdbc.*` settings belong in `VF_HOME` instead of the JAR.

## Persistence architecture

VF uses MyBatis rather than JPA. DAO interfaces and service boundaries live in Java; SQL mappings
live in module resources. The canonical fresh-install PostgreSQL schema is maintained under
`config/database/postgresql`.

Schema scripts are deliberately external to application startup:

- fresh databases require the DDL, functions, views, and seed scripts to be applied manually;
- existing databases require the applicable manual migration scripts before new code starts;
- no migration framework discovers or applies scripts automatically;
- test-only constraints must not be installed in production.

Long-running work does not hold a database transaction open. VF uses short transactions to claim
or fence work, performs the external work outside the transaction, then rechecks ownership before
committing the outcome.

## Messaging architecture

Application code asks the `messaging` SPI for a provider and does not use broker client classes
directly. Kafka is the primary durable stream for lambda work and cross-process notifications. SQS is
used for asynchronous Lambda results. An in-memory loopback broker exists for tests and one-JVM
tools, but it cannot connect separately running API and worker processes.

Kafka topics have two consumption patterns:

- shared work topics are processed once by a consumer group;
- broadcast topics, currently including `cache-invalidation`, use a per-server group so every JVM
  receives each event.

Topic names in `messaging/.../Topics.java` must stay synchronized with
`config/kafka/bin/create_topics.sh`.

## Process 1: user and registry operations

```text
HTTP request
  -> API controller/action
  -> authenticate API_KEY
  -> service authorization
  -> transactional DAO/MyBatis operation
  -> commit
  -> response envelope
```

Organization relationships, active location assignments, and ownership are resolved on the server.
Writes that affect cached data also enqueue cache invalidation in the same transaction. After
commit, the local cache is evicted; a worker later publishes the durable invalidation to Kafka so
other JVMs evict their copies.

## Process 2: lambda development and deployment

```text
create lambda and draft configuration
  -> choose MAJOR/MINOR/PATCH bump
  -> upload Python source archive
  -> worker receives lambda-code operation
  -> build container image
  -> deploy/test lambda version
  -> promote to PRODUCTION
  -> assign production version to locations
```

The API records configuration and publishes deployment work. The worker compiles the lambda into a
container image and deploys it to Lambda in production or the local Docker/Lambda emulator path in
local mode. Version transitions preserve a production version and fallback for rollback. Assigning
or reactivating a lambda emits a reset event for the assignment's private state generation.

## Process 3: event-driven lambda execution

```text
device/location change
  -> TriggerEvent on lambda-input
  -> worker selects active matching assignments
  -> hydrate TriggerEventData once
  -> dispatch to each selected assignment
  -> aggregate/queue chronological inputs
  -> invoke lambda
  -> persist result or a durable retry
  -> publish default-lane completion
  -> advance the logical run
```

Hydration combines event deltas with current database snapshots for the location, devices, and
users. A stable event identity supports deduplication. Default-lane invocation waits for the Lambda
attempt and acknowledges Kafka only after the attempt has a durable outcome.

When a retryable failure occurs, VF updates run state and inserts the retry row atomically. A worker
job claims due retries with a lease and executes them directly; a retry is not routed through Kafka
again. Generation and invocation tokens reject late results from superseded attempts. See
[Lambda Invocation Retries](lambda-invocation-retries.md).

## Process 4: schedules and resets

Scheduled lambda execution is assignment-specific. The scheduler loads the identified active
assignment, verifies its location, hydrates current state, and submits a run without using the
location-wide trigger fan-out path.

Reset is also assignment-specific. A reset event travels through `lambda-reset`, advances the private
variable generation monotonically, and runs the lambda with a current snapshot. Old private variables
then become invisible, while explicitly location-shared variables remain unchanged.

Cron jobs are discovered by the worker and run under database-backed locks. Multiple workers may
exist, but lock and fencing rules prevent the same non-concurrent job outcome from being committed
twice.

## Process 5: lambda state and alert creation

A lambda receives a short-lived `LAMBDA_API_KEY` and API host list as part of its input. It can read and
write variables or submit an alert without receiving a user credential.

The transition-alert example follows this order:

```text
load previous private snapshot
  -> reconstruct current state
  -> detect safe-to-dangerous edges
  -> create idempotent alert
  -> save the new snapshot
```

Alert creation and variable update are separate HTTP transactions. Creating the alert first is safe
because an idempotency key makes replay return the original alert rather than duplicate it. See
[Lambda Alert Processing](lambda-alert-processing.md).

## Process 6: reports

Report deployment and execution are separate processes:

```text
YAML metadata + SQL
  -> offline validation
  -> explicit transactional registration
  -> database report/group assignment
  -> API or worker loads the report
  -> bind parameters and execute SQL
  -> CSV/ZIP output
  -> database or S3-backed history
```

Runtime parameters use prepared MyBatis bindings (`#{p0}` through `#{p9}`); the stored report query
is trusted deployment SQL. Report registration is explicit and does not watch the filesystem.
Scheduled reports are selected by the worker, executed outside the schedule transaction, then
fenced and persisted together with schedule advancement.

## Process ownership summary

| Capability | API | Worker | Database | Kafka/AWS |
| --- | ---: | ---: | ---: | ---: |
| User-facing CRUD and authentication | Yes | No | State | — |
| Lambda configuration and upload request | Yes | No | State | Kafka publish |
| Lambda build/deployment | No | Yes | Status | ECR/Lambda or local Docker |
| Trigger processing and lambda invocation | Produces | Yes | Run state | Kafka + Lambda/SQS |
| Scheduled jobs and outbox dispatch | No | Yes | Claims/locks | Kafka/AWS destinations |
| Report REST execution | Yes | Scheduled only | Definitions/history | S3 for large output |
| Cross-process cache invalidation | Consumes | Publishes/consumes | Durable outbox | Kafka broadcast |

## Operational observability

The API listens on port `8086` and exposes its lambda-workload management endpoint on loopback port
`8087`. The worker listens on `8082`, with management on loopback port `8083`; its shutdown endpoint
is enabled there. Lambda workload metrics describe live in-process work rather than a durable queue
depth. Their fields and interpretation are documented in [Lambda Workload Metrics](lambda-workload-metrics.md).
