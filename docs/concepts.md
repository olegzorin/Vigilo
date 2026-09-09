# Vigilo Framework concepts

## What Vigilo Framework is

Vigilo Framework (VF) is a multi-tenant platform for building event-driven monitoring systems. It
provides the server-side infrastructure for registering organizations, users, locations, and
devices; developing and deploying lambdas; routing events to those lambdas; persisting execution state;
and exposing results through APIs and reports.

VF is a framework, not a finished monitoring product. Domain behavior lives in independently
developed Python lambdas. A product can therefore define its own device models, trigger conditions,
state interpretation, and alert policy without adding that policy to the framework itself.

The main design goals are:

- isolate data and authorization by organization;
- turn device, location, reset, and schedule signals into reliable lambda executions;
- let lambda code evolve independently from the Java server;
- preserve enough state to recover safely from duplicate delivery and transient failure;
- support both interactive APIs and asynchronous processing;
- provide database-driven reports without requiring one Java class per report;
- keep infrastructure integrations behind replaceable boundaries.

## The domain model

### Organizations and tenancy

An **organization** is the principal tenant boundary. Organizations may form a hierarchy, which
allows a parent organization to administer or inherit selected resources from descendants.
Authorization is evaluated by server-side services; callers cannot establish ownership merely by
including an organization ID in a request.

A **user** belongs to an organization and may be assigned to one or more locations. Organization
administrators manage users, locations, lambda access, and report groups within their permitted
hierarchy.

### Locations and devices

A **location** is the operational unit at which monitoring occurs. Users and devices are assigned
to locations over time, so assignment records have effective dates rather than being simple static
links.

A **device** has a type and an organization owner. Its current state is stored separately from its
identity. Device and location state changes can become triggers for lambdas assigned to the affected
location.

### Lambdas, versions, and development teams

A **Lambda** is an independently developed Python plugin that interprets events delivered by VF
and decides how to respond. The server receives events and invokes the relevant Lambdas; a Lambda
runs in response to those inputs, using its domain logic and persisted state to choose a reaction.

External developers can add or modify event-processing logic through Lambdas without rebuilding
the VF framework. Each Lambda is built, versioned, and deployed independently, with a development
team owning its definition and release lifecycle. The Java platform manages configuration, uploaded
source, container-image deployment, assignments, inputs, credentials, execution, and results; the
Python implementation remains outside this repository.

A **Lambda version** is a VF plugin release. An **AWS Lambda function version** identifies a version
of the AWS function that executes plugin code.

A Lambda version moves through a controlled lifecycle:

```text
DRAFT -> TESTING -> PRODUCTION -> FALLBACK/ARCHIVED
          |                         ^
          +------ DISCARDED --------+
```

There is at most one development version, one production version, and one fallback version for a
lambda. Developers choose a major, minor, or patch bump; VF derives the version number from a
persistent high-water mark. See [Automatic Lambda Version Numbering](lambda_versioning.md) for the
detailed rules.

### Lambda assignments

A **Lambda assignment** connects a production or testing Lambda version to a location and supplies the runtime
configuration for that installation. It defines when the lambda is active, which triggers it accepts,
and which resources are visible to it.

Assignments are the unit of runtime isolation:

- each run belongs to an assignment;
- lambda API keys identify an assignment and location;
- private lambda variables are scoped to an assignment generation;
- reset events advance that generation so stale invocations cannot restore old private state;
- assignment start and end dates determine whether new work may be accepted.

### Events, inputs, and runs

A **trigger event** describes a location or device state change. Before a lambda receives it, VF
hydrates the event with the current location, assigned devices, users, and relevant state. The
result is `TriggerEventData`.

A **Lambda run** is a logical execution for one assignment. Several inputs may be aggregated into a
run and are ordered chronologically. VF separates the logical run from individual invocation
attempts so that retrying an infrastructure failure does not create a new business run.

The default execution lane is synchronous and reports completion through Kafka. A separate
asynchronous lane is used for scheduled asynchronous work and receives completion through the lambda
result queue. The persistence and fencing rules prevent old attempts from completing a newer run.

### Variables and alerts

**Lambda variables** let a lambda persist data between invocations. Private variables belong to the
assignment's current generation; location-shared variables deliberately have a wider scope and
therefore require more careful concurrency design.

An **alert** is a durable occurrence submitted by an authenticated lambda. VF validates resource
access, derives trusted organization and lambda identity from the lambda key, and makes creation
idempotent. The lambda—not VF—decides whether a state is dangerous. The example policy and complete
flow are documented in [Lambda Alert Processing](lambda-alert-processing.md).

### Reports

A **report** is a database-registered definition made from YAML metadata and SQL. Reports are
organized into report groups, which are assigned to organizations and may be inherited through the
organization hierarchy.

Report definitions are deployment inputs under `report/database/definitions`; they are not bundled
inside the report JAR. Registration validates and writes them to the database. The running server
then discovers them through ordinary database queries, so SQL/YAML reports can be added without a
server rebuild. A rebuild is needed only for behavior outside the existing report engine.

On-demand and scheduled reports share the same execution engine. Summary output is generated as
CSV in a ZIP file; small output is kept in the database and larger output is stored through the S3
abstraction. See [Report Module Implementation](report-implementation.md).

## Reliability model

VF assumes that messages can be duplicated, processing can stop mid-flight, and more than one
server may be running.

Its main reliability techniques are:

- stable event identities and idempotent business operations;
- database transactions around related state changes;
- Kafka for durable, ordered inter-process work whenever it satisfies the required invariant;
- database outboxes when a database write and future work must be committed atomically;
- leases, claims, and generation tokens for ownership and stale-worker fencing;
- retry of a logical run without losing its identity;
- database job locks for non-concurrent scheduled maintenance;
- cross-JVM cache invalidation delivered at least once through an outbox and Kafka.

The database-backed retry path is explained in [Lambda Invocation Retries](lambda-invocation-retries.md),
and cache consistency is explained in [Cache Invalidation Transactional Outbox](cache-invalidation-outbox.md).

## Security model

VF uses different credentials for different actors:

- user API keys authenticate interactive user requests;
- short-lived lambda API keys authenticate a specific assignment invocation;
- signing keys stored in the database issue and validate JWTs;
- a key-encryption key supplied through `VF_KEK` protects encrypted configuration and key material;
- database and real-AWS credentials are external configuration, never request parameters.

Services derive trusted tenant, user, lambda, assignment, and location context from authenticated data.
Database rows and MyBatis queries enforce the remaining resource relationships. A successful
authentication therefore does not bypass organization or assignment authorization.

## What to read next

Read [Architecture and Processes](architecture-and-processes.md) for module boundaries and the
end-to-end runtime flows. Then use [Local Installation and Deployment](local-installation.md) to
build and run a complete local server.
