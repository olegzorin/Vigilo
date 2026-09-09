# New Event Processing Model

Related runtime flow: [Lambda Alert Processing](lambda-alert-processing.md) describes how a lambda uses
hydrated `TriggerEventData`, lambda variables, and the lambda-authenticated alert API to detect and persist
dangerous state transitions.

## Key Changes

- `TriggerEvent` replaces `LambdaEvent` as the event published to and consumed from the `lambda-input` Kafka topic.
- `TriggerEventData` replaces `AnalyticInput` throughout lambda execution, including:
  - `LambdaInput.inputs`
  - `LambdaRun.inputs`
  - pending lambda inputs
  - trigger aggregation and chronological input sorting
- `LambdaEvent` and `AnalyticInput` have been removed.
- A new service-layer boundary, `TriggerEventDataService`, hydrates `TriggerEventData`.
- Event processing now follows this flow:

  ```text
  TriggerEvent
      -> select active assignments by location and trigger
      -> hydrate TriggerEventData once
      -> dispatch the same TriggerEventData instance to every selected assignment
      -> submit or queue each lambda run
  ```

- `TriggerEventDataServiceImpl` hydrates fields according to their source:
  - Auto-generated:
    - `key`
  - Copied from `TriggerEvent`:
    - `time`
    - `trigger`
    - `locationId`
    - `newLocationState`
    - `deviceUuid`
    - `newDeviceState`
  - Loaded from the database:
    - `location`
    - `locationDevices`
    - `locationUsers`
- Location hydration includes location metadata and current state.
- Active location devices are converted to `LocationDeviceSnapshot`, including their current state.
- Hydration runs in a read-only transaction.
- Hydrated collections and the top-level device-state map are protected from mutation before shared dispatch.
- `LambdaClientService.publishLambdaEvent` has been renamed to `publishTriggerEvent`.
- `LambdaInputListener` now deserializes `TriggerEvent`.
- Device events are included in the supported lambda-version trigger mask.
- The event-processing and lambda-input contracts are clean-slate models. Old Kafka events, pending inputs, and lambda-side payload shapes are not supported.

## Validation

- The full `core`, `worker`, and `api` reactor test run passed.
- Focused hydration and shared-dispatch tests passed after the final changes.
- `LambdaRunMapper.xml` parses successfully.
- `git diff --check` passes.
- No stale `LambdaEvent` or `AnalyticInput` code references remain.

## Open Issues and Questions

### Scheduled Events

Scheduled execution uses a separate assignment-specific path. The scheduler creates
a `ScheduledEvent` containing the location, assignment, and fired schedule IDs and
dispatches it directly within the worker. The scheduled-run service:

- loads only the identified active assignment;
- verifies that it still belongs to the event location;
- hydrates the current location snapshot;
- copies the fired schedule IDs into `TriggerEventData`;
- submits the run only for that assignment.

Scheduled events therefore do not use Kafka, the location-scoped trigger selector, or
the shared fan-out path.

### Reset Events

Reset is handled as a separate assignment lifecycle operation rather than a generic
`TriggerEvent`. A `ResetEvent` identifies one active assignment and is published to
the `lambda-reset` topic when:

- a lambda is newly assigned to a location;
- an inactive assignment becomes active because its `endDate` changes from the past
  to `null` or a future date.

The event also carries a private-variable generation. After loading the identified
active assignment and verifying its location, the reset consumer advances the
assignment's generation monotonically before hydrating the current location snapshot
and submitting the reset run. Private-variable reads and writes are scoped to the
generation embedded in the lambda key, so earlier variables become invisible and an
older invocation cannot repopulate the active namespace. Duplicate or delayed reset
events cannot move the generation backward. Location-shared variables are unaffected.

### Scheduled Asynchronous Executions

The old models carried asynchronous data-request results through `LambdaEvent.dataRequests`
and `AnalyticInput.data`. Data-request execution is no longer supported. Scheduled events
use the dedicated asynchronous invocation lane, which is serialized independently from the
default lane and completed directly from the Lambda asynchronous SQS destination. The
`lambda-run-completion` Kafka topic remains only for default-lane execution.
