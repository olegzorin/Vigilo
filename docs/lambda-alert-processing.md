# Lambda Alert Processing

## Purpose and scope

Lambda alert processing lets a running lambda compare the latest location and device state with a state
snapshot saved by an earlier invocation and create a durable alert when a value enters a dangerous
state.

The implementation consists of two cooperating parts:

1. The demo Python lambda reconstructs the current state, loads and updates its previous-state snapshot,
   evaluates dangerous transitions, and calls the alert API.
2. The VF API authenticates the lambda, authorizes every referenced resource, makes creation idempotent,
   derives trusted ownership and lambda identity, and persists the alert.

The demo is an example policy, not a general rules engine. The server persists authorized alert
occurrences but does not decide whether a state is dangerous.

Relevant implementations:

- Demo lambda: [`examples/dangerous_state_alert_lambda.py`](examples/dangerous_state_alert_lambda.py)
- REST adapter: `api/.../lambda/alert/LambdaAlertController` and `LambdaAlertAction`
- Core boundary: `core/.../service/lambda/LambdaAlertServiceImpl`
- Persistence: `core/.../dao/impl/LambdaAlertDaoImpl` and `vf/sqlmaps/LambdaAlertMapper.xml`
- Public contract: `POST /vf/alerts` in `api/doc/openapi.yaml`

## End-to-end flow

```text
TriggerEvent / ScheduledEvent / ResetEvent
    -> TriggerEventData hydration
    -> LambdaInput.inputs[]
    -> running lambda receives LAMBDA_API_KEY and apiHosts
    -> GET /vf/variables/danger-monitor-state-v1
    -> reconstruct current location/device snapshot for each input
    -> compare previous and current snapshots
    -> if a value entered a dangerous state:
         POST /vf/alerts
    -> PUT /vf/variables/danger-monitor-state-v1
    -> return LambdaOutput
```

Alert creation and variable update are separate HTTP calls and separate database transactions. They
are intentionally ordered as alert first and snapshot second. Idempotent alert creation makes that
ordering safe under retry; see [Failure and retry behavior](#failure-and-retry-behavior).

## Lambda input used by the demo

The top-level `LambdaInput` supplies:

| Field | Use |
| --- | --- |
| `id` | Lambda-assignment ID used as part of the alert idempotency key. |
| `lambdaId`, `lambdaVersionId` | Present in the input, but not sent as trusted alert identity. The server derives these from `LAMBDA_API_KEY`. |
| `runId` | Correlation value included in the alert request. |
| `apiKey` | Time-limited lambda JWT sent in the `LAMBDA_API_KEY` header. |
| `apiHosts` | Candidate VF API hosts. The demo uses the first host. |
| `inputs` | One or more `TriggerEventData` objects processed in ascending `time` order. |

Each `TriggerEventData` contains two kinds of state information:

| Source | Fields |
| --- | --- |
| Event delta | `key`, `time`, `locationId`, `newLocationState`, `deviceUuid`, `newDeviceState` |
| Hydrated database snapshot | `location.currentState`, `locationDevices[].currentState` |

`key` is the stable identifier used to deduplicate the alert for one input event. The hydrated
location and device lists provide the complete state context available when the input was created;
the explicit event delta identifies the new value that caused the input.

### Current-state reconstruction

For every input, the demo constructs this snapshot:

```json
{
  "locationId": 11,
  "locationState": "HOME",
  "devices": {
    "device-1": {
      "alarm": false,
      "fallStatus": "idle"
    }
  },
  "eventTime": 1750000000000
}
```

The reconstruction rules are:

1. Start with `location.currentState` and every `locationDevices[].currentState`.
2. When `newLocationState` is non-null, use it instead of `location.currentState`.
3. When `deviceUuid` and `newDeviceState` are present, merge that device delta over the hydrated
   state for the matching device.
4. Preserve all other hydrated device states unchanged.

Explicit event values win because they represent the transition being evaluated and may be newer
than the database snapshot used during hydration.

## Previous-state variable

The demo uses the variable named `danger-monitor-state-v1`:

```http
GET /vf/variables/danger-monitor-state-v1
LAMBDA_API_KEY: <lambda JWT>
```

No `shared=true` query parameter is supplied. The variable therefore belongs to the authenticated
lambda assignment and its current private-variable generation.

- HTTP 200 with `application/octet-stream` returns the UTF-8 JSON snapshot.
- HTTP 204 means no previous snapshot exists.
- API failures can still use HTTP 200 with a nonzero `resultCode`; the demo checks the response
  envelope when the content type is not `application/octet-stream`.

After processing an input, the current snapshot is stored as binary JSON:

```http
PUT /vf/variables/danger-monitor-state-v1
LAMBDA_API_KEY: <lambda JWT>
Content-Type: application/octet-stream

<UTF-8 JSON bytes>
```

### First invocation and reset behavior

When the variable is absent, the demo treats the current state as a baseline and does not alert.
This prevents initial deployment from reporting every already-dangerous value as a new transition.
It also means the first dangerous state observed after installation is not reported unless an older
baseline already exists.

A lambda reset advances the assignment-private variable generation. The old snapshot becomes invisible,
so the first invocation in the new generation establishes another baseline. A location-shared
variable would survive resets, but would also be shared by every lambda at that location and would need
an explicit concurrency and ownership design. The demo intentionally uses private scope.

## Dangerous-transition policy

The demo alerts only on an edge from a non-dangerous value into a dangerous value. Merely observing
the same dangerous value again does not produce another alert.

### Location transitions

Location state comparison is case-insensitive. The configured dangerous states are:

- `FALL_EMERGENCY`
- `FIRE`
- `PANIC`

Example:

```text
HOME -> FALL_EMERGENCY       alert
FALL_EMERGENCY -> FALL_EMERGENCY   no alert
FALL_EMERGENCY -> HOME       no alert
```

### Device boolean transitions

The following fields are dangerous when their new value is the Boolean value `true` and the previous
value was not `true`:

- `alarm`
- `smoke`
- `coAlarm`
- `panic`

The comparison is deliberately type-sensitive: the string `"true"` is not treated as Boolean
`true`.

### Fall-status transitions

`fallStatus` comparison is case-insensitive. These states are dangerous:

- `fall_detected`
- `fall_confirmed`
- `calling`

If one event creates several dangerous transitions, the lambda sends one alert containing several
reasons. The policy constants are currently hard-coded in the demo. A production lambda should obtain
them from validated lambda configuration or device-type metadata.

## Alert API contract

The lambda creates an alert with:

```http
POST /vf/alerts
LAMBDA_API_KEY: <lambda JWT>
Content-Type: application/json
```

Example request:

```json
{
  "idempotencyKey": "101:550e8400-e29b-41d4-a716-446655440000:danger-transition-v1",
  "ruleId": "danger-transition-v1",
  "severity": "CRITICAL",
  "locationId": 11,
  "occurredAt": 1750000000000,
  "runId": 1750000001000,
  "eventKey": "550e8400-e29b-41d4-a716-446655440000",
  "reasons": [
    {
      "resourceType": "DEVICE",
      "resourceId": "device-1",
      "field": "alarm",
      "previousValue": false,
      "newValue": true
    }
  ]
}
```

Request constraints:

| Field | Constraint |
| --- | --- |
| `idempotencyKey` | Required, 1-250 characters. |
| `ruleId` | Required, 1-100 characters. |
| `severity` | `WARNING` or `CRITICAL`. |
| `locationId` | Positive integer. |
| `occurredAt` | Positive Unix epoch milliseconds. |
| `runId` | Positive 64-bit integer. |
| `eventKey` | Required, 1-100 characters. |
| `reasons` | 1-20 entries. Serialized value must be at most 16,000 characters and valid for the configured database character set. |
| `reason.resourceType` | `LOCATION` or `DEVICE`. |
| `reason.resourceId` | Required, 1-150 characters. |
| `reason.field` | Required, 1-100 characters. |
| `previousValue`, `newValue` | Optional JSON values. |

Successful creation returns the normal `ActionResponse` envelope plus:

```json
{
  "resultCode": 0,
  "alertId": "7305552e-5123-4b61-a183-cc62b5293ae4",
  "status": "OPEN",
  "created": true
}
```

An identical retry returns the same `alertId` with `created=false`. As with other JSON VF APIs,
clients must inspect `resultCode`; application errors normally still use HTTP 200.

## Server-side processing

`LambdaAlertServiceImpl.createAlert()` performs these steps:

1. Validate required fields, sizes, positive numeric values, reason count, and serialized reason size.
2. Parse and verify `LAMBDA_API_KEY` through `LambdaClientService`.
3. Require the submitted `locationId` to equal the location in the lambda JWT.
4. Authorize every reason:
   - a `LOCATION` reason must use the decimal request location ID as `resourceId`;
   - a `DEVICE` reason must reference a device actively assigned to the JWT location.
5. Serialize the reason list into `changes_json`.
6. Create a SHA-256 hash of the normalized submission for idempotency conflict detection.
7. Generate a UUID alert ID and set server-controlled values:
   - `alert_type = DANGEROUS_STATE_CHANGE`;
   - `status = OPEN`;
   - lambda assignment, lambda, and lambda-version IDs from the verified JWT.
8. Insert the alert. The mapper derives `organization_id` from the non-deleted `locations` row.
9. If the unique idempotency key already exists, load the existing row for this lambda assignment:
   - matching payload hash: return the existing row with `created=false`;
   - missing row or different hash: reject the request as an idempotency-key conflict.

The lambda cannot submit trusted `organizationId`, `lambdaAssignmentId`, `lambdaId`, `lambdaVersionId`, alert
status, or alert type.

`runId`, `eventKey`, and `occurredAt` are currently validated for shape but are not cross-checked
against the server's current run record. They are correlation metadata supplied by the authenticated
lambda, not server-attested execution identity.

## Idempotency design

The demo constructs the key as:

```text
<lambdaAssignmentId>:<TriggerEventData.key>:<ruleId>
```

The database has a global unique index on `idempotency_key`. The core service also stores a payload
hash so accidental reuse for different content does not silently return an unrelated alert.

The insert runs in a separate `REQUIRES_NEW` transaction because a PostgreSQL unique-key violation
aborts the insert transaction. Allowing that transaction to roll back before selecting the existing
row keeps duplicate handling reliable.

The server scopes the duplicate lookup by both idempotency key and lambda-assignment ID. A collision
with another assignment is rejected and does not disclose that assignment's alert.

## Database persistence

Alerts are stored in `lambda_alerts`.

| Column | Source and meaning |
| --- | --- |
| `alert_id` | Server-generated UUID primary key. |
| `idempotency_key` | Client-supplied stable key; globally unique. |
| `payload_hash` | SHA-256 hash used to distinguish replay from conflicting reuse. |
| `organization_id` | Derived from `locations` during insert. |
| `location_id` | Authorized request location. |
| `device_uuid` | Filled when all device reasons reference exactly one distinct device; otherwise null. |
| `alert_type` | Server-controlled `DANGEROUS_STATE_CHANGE`. |
| `severity` | `WARNING` or `CRITICAL` from the validated request. |
| `status` | Server-controlled `OPEN` on creation. |
| `rule_id` | Lambda policy/rule version identifier. |
| `occurred_at` | Event occurrence time supplied by the lambda. |
| `created_at` | Database creation time. |
| `lambda_assignment_id` | Derived from the lambda JWT. |
| `lambda_id` | Derived from the lambda JWT. |
| `lambda_version_id` | Derived from the lambda JWT. |
| `run_id` | Lambda-supplied run correlation value. |
| `event_key` | Lambda-supplied input-event correlation value. |
| `changes_json` | Serialized previous/new values for every reason. |
| `acknowledged_at`, `acknowledged_by` | Reserved for acknowledgement lifecycle. |
| `resolved_at`, `resolved_by` | Reserved for resolution lifecycle. |

Indexes support:

- idempotent lookup by `idempotency_key`;
- location/status timelines by `(location_id, status, occurred_at)`;
- device timelines by `(device_uuid, occurred_at)`.

There are currently no alert-specific foreign keys. Alert history is therefore not cascade-deleted
when assignments or devices are cleaned up. There is also no retention/archival job.

`changes_json` is stored as text so its serialized application contract stays independent of
PostgreSQL JSON operators and storage details.

## Failure and retry behavior

The workflow is not one distributed transaction. Its ordering gives the following outcomes:

| Failure point | Persisted result | Retry behavior |
| --- | --- | --- |
| Variable GET fails | No alert and no new snapshot. | The lambda returns `errorMessage`; a later retry starts from the old snapshot. |
| Alert POST fails | No new snapshot. | The same transition is evaluated and alert creation is retried. |
| Alert POST succeeds, variable PUT fails | Alert exists; snapshot is old. | The same idempotency key returns the existing alert, then the lambda retries the snapshot write. |
| Variable PUT succeeds | Alert, if any, and new snapshot are durable. | Reprocessing the same state normally finds no new dangerous edge. |
| Lambda crashes after alert response but before PUT | Same as alert-success/PUT-failure. | Database uniqueness prevents a duplicate alert. |

The demo saves the snapshot after every chronologically sorted input, not only once after the batch.
This lets later inputs in the same invocation compare against the result of earlier inputs.

The variable API does not provide compare-and-set semantics. The demo therefore assumes relevant
executions for one assignment do not update this snapshot concurrently. DEFAULT and ASYNC lanes are
independently serialized by the platform and can overlap; using this demo from both lanes can cause
last-write-wins snapshot races and inconsistent transition comparisons. A production lambda should
confine this state machine to one lane or add a server-side concurrency/fencing design.

## Lambda output behavior

On success, the demo returns:

```json
{
  "startCode": 0,
  "startTime": 1750000000000,
  "endTime": 1750000000123
}
```

On any exception it returns the same timing fields plus `errorMessage`. The worker maps a nonblank
`errorMessage` to a lambda error and applies the normal invocation/retry processing. The demo assumes
the surrounding VF Lambda engine has already completed the required run-start handshake.

## Security properties

- All variable and alert calls use the time-limited `LAMBDA_API_KEY`, not a user `API_KEY`.
- Private variable access is scoped by assignment ID and variable generation from the JWT.
- Alert location access is restricted to the JWT location.
- Device reasons require an active device assignment to that location at request time.
- Organization and lambda identity are resolved by the server.
- Duplicate lookup cannot return another assignment's alert.
- Reason count, field sizes, serialized size, and supported database characters are bounded.

The server does not inspect whether the submitted values are clinically or operationally correct.
Authorization proves that the lambda may report on the resources; it does not prove the policy's
interpretation of their state.

## Deployment

Fresh databases receive `lambda_alerts` from:

- `config/database/postgresql/ddl/create_tables.sql`

Existing databases must apply the matching manual migration before deploying code that can call the
alert mapper:

- `config/database/postgresql/migrations/20260821_create_bot_alerts.sql`

VF does not discover or execute these migrations at build or application startup. Deploying the API
before the table exists causes alert creation to fail at the persistence layer.

## Current limitations and extension points

- Dangerous-state values are hard-coded in the demo instead of being device-type configuration.
- The first invocation after installation or reset establishes a baseline and cannot report a
  transition without previous state.
- Only alert creation exists. There are no alert search, detail, acknowledge, resolve, or reopen APIs.
- Lifecycle columns are reserved but not yet updated by application code.
- Alert creation does not yet publish an SNS/Kafka notification.
- Alert mapper calls do not currently have dedicated `LambdaWorkloadMetric` instrumentation.
- There is no retention policy or archival process.
- `runId`, `eventKey`, and `occurredAt` are not server-fenced against the current invocation.
- The demo uses only the first `apiHosts` entry and does not implement host failover.
- Cross-lane concurrent variable updates are last-write-wins.

These constraints should be addressed according to the consuming product's alert lifecycle,
notification, regulatory, and concurrency requirements rather than being generalized in the demo.

## Verification coverage

The implementation includes focused coverage for:

- authorized creation and server-derived lambda identity;
- location/device access rejection;
- identical idempotent replay;
- conflicting idempotency-key reuse;
- API request deserialization and response mapping;
- presence of the alert table, lifecycle columns, indexes, and manual PostgreSQL migration;
- demo transition behavior and Python syntax;
- OpenAPI, Postman, and MyBatis document syntax.

A live DAO integration test requires a provisioned PostgreSQL database and an applied `lambda_alerts`
migration. The checked-in schema contract tests do not replace executing the migration and mapper
against PostgreSQL before production rollout.
