# Lambda Workload Metrics

## Goals

Lambda workload metrics describe the expensive work associated with lambda runs. Their primary purpose is
to make sustained load, bursts, slow operations, and failures visible without instrumenting every
method in the system.

The monitored workload has two parts:

1. Database and workflow operations that create, load, advance, retry, and complete lambda runs, load
   their inputs and runtime data, and read or write lambda variables.
2. Lambda execution in AWS Lambda, including the execution time reported for an individual run and the
   total time required to process that run end to end.

These two parts currently use different collection and storage mechanisms:

- `LambdaWorkloadMetrics` keeps recent workflow and SQL workload in memory and exposes it through the
  `lambdaworkload` Actuator endpoint.
- Lambda execution and end-to-end processing times are stored with individual lambda-run results in the
  database. They are not currently included in the `lambdaworkload` endpoint.

The metrics are operational measurements, not billing records, distributed traces, or exact AWS
CloudWatch Lambda metrics. In particular, they do not replace AWS metrics for billed duration, cold
starts, concurrency, throttling, or asynchronous event age.

## Live workload metric types

Every live metric is identified by a `LambdaWorkloadMetric.Operation`. Public metric names use a prefix
that identifies what the duration represents.

### `workflow.*`

A workflow metric measures an entire application-level lambda-run state transition. It can include
transaction handling, locking, several SQL statements, input conversion, and application logic.

| Metric | Meaning |
| --- | --- |
| `workflow.putNewRun` | Attempts to install a new run or queue its input behind an existing run. Both transactional entry points contribute to this series. |
| `workflow.putNextRun` | Completes the current transition and prepares the next pending run. |
| `workflow.markLambdaRunNotStartedIfUnchanged` | Locks a run and resets its started state when the invocation identity is still current. |
| `workflow.updateLambdaRunRetry` | Locks a run, validates retry eligibility, updates retry state, and persists retry work. |

Workflow metrics answer questions such as “How expensive is accepting a new lambda input?” They should
not be added to their nested SQL durations: the time ranges overlap.

### `sql.*`

An SQL metric measures one selected MyBatis mapper invocation. These metrics isolate the database
operations most closely connected with lambda-run throughput and payload loading.

| Area | Metrics |
| --- | --- |
| Runtime assignment and trigger loading | `sql.getActiveLambdaRuntimeAssignmentById`, `sql.selectScheduledLambdaAssignments`, `sql.selectRuntimeAssignmentsForLocation`, `sql.selectTriggerLocationMetadata`, `sql.selectTriggerLocationDeviceMetadata`, `sql.selectTriggerLocationHydration` |
| Run state | `sql.selectLambdaRunForUpdate`, `sql.selectLambdaRun`, `sql.updateLambdaRunPendingCount`, `sql.updateLambdaRunAsStarted`, `sql.updateLambdaRunSubmitAsync`, `sql.completeLambdaRun` |
| Completion recovery | `sql.selectLambdaRunsRequiringCompletion`, `sql.selectAsyncLambdaRunsRequiringCompletion` |
| Pending inputs | `sql.updateLambdaPendingInput`, `sql.insertLambdaPendingInput`, `sql.selectLambdaPendingInputs` |
| Result persistence | `sql.insertLambdaRunsInfo` |
| Lambda variables | `sql.selectLambdaVariables`, `sql.insertLambdaVariable`, `sql.updateLambdaVariable`, `sql.deleteLambdaVariable` |

The variable series intentionally combine assignment-variable and location-variable operations of the
same kind. Those calls usually originate in the API process, so they appear in the API endpoint and
not in the worker endpoint.

The list is selective. An unannotated mapper method is not collected merely because it accesses the
database.

## Fields reported for each live operation

`LambdaWorkloadMetricSnapshot` returns one row per operation observed in the current JVM. The row has
three time windows: one hour, six hours, and three days.

| Field pattern | Meaning |
| --- | --- |
| `name` | Stable public operation name, such as `workflow.putNewRun` or `sql.selectLambdaVariables`. |
| `elapsed` | Time since this operation's monitor was first created, formatted as minutes, hours/minutes, or days/hours/minutes. |
| `avgCPM_<window>` | Average calls per completed minute in the available part of the window. This is a fractional `double`. |
| `maxCPM_<window>` | Largest call count in any completed one-minute slice. |
| `avgCallTimeMicros_<window>` | Total measured duration divided by total calls, in microseconds. |
| `calls_<window>` | Number of attempted calls in the window. |
| `failures_<window>` | Number of those calls that completed by throwing an exception. |

The concrete suffixes are `_1Hour`, `_6Hours`, and `_3Days`.

Calls are attempts, not only successful logical operations. A failed call contributes to call count
and duration and also increments the failure count. If retry advice invokes an annotated operation
again, each execution attempt is measured. This is intentional because retries consume real worker
and database capacity.

There are no latency-percentile fields. The collector stores only count and total duration per minute,
which is sufficient for average duration but cannot produce a statistically valid per-call p90 or
p99.

## How live metrics are collected

Methods selected for monitoring carry the annotation:

```java
@LambdaWorkloadMetric(operation = sqlSelectLambdaVariables)
```

`LambdaWorkloadMetricAspect` wraps an annotated method. It reads a monotonic start time from
`System.nanoTime()`, invokes the method, and records elapsed time in a `finally` block. Consequently,
both normal returns and thrown exceptions are recorded. Durations are converted from nanoseconds to
microseconds before entering the collector.

Collection is enabled by default. It can be disabled at process startup with:

```properties
vf.lambdaWorkloadMetrics.enabled=false
```

The annotation stores this setting in a static constant when its class is initialized. Changing the
property in a running process does not enable or disable collection; the process must be restarted.

`LambdaWorkloadMetrics` owns a process-local map keyed by the operation enum. The map is a synchronized
`EnumMap`; creating a monitor for the first observation and iterating it for a snapshot are therefore
serialized safely. Each operation has two thread-safe `WorkloadMonitor` instances:

- one records every attempt and its duration;
- one records failed attempts, allowing failure totals to be calculated for each window.

Each monitor retains up to three days as 4,320 one-minute slices and uses 50 accumulator branches to
reduce contention between request threads. Old slices fall out as time advances.

Only completed one-minute slices are returned. The currently active minute is omitted because its
rate is not final. This has several consequences:

- a newly observed operation appears in the result, but its counters can remain zero until the first
  minute closes;
- averages use the number of available completed slices since that operation was first observed, up
  to the requested window;
- empty completed minutes are included in the average, so `avgCPM` represents sustained rate rather
  than an average over active minutes only.

Before any annotated operation is observed, the endpoint returns an empty JSON array. Metrics are not
persisted; restarting a process resets its live history.

## Lambda execution and processing metrics

Individual lambda-run results persist two millisecond values in `lambda_run_results`:

| Field | Meaning |
| --- | --- |
| `execution_time` | Best available duration for executing or invoking the lambda. |
| `processing_time` | End-to-end time from the run request timestamp until result processing creates the persisted run information. It can include queueing, retries, transport, and worker-side handling. |

For a DEFAULT-lane request-response invocation, `execution_time` prefers the lambda output's `startTime`
and `endTime`. If those timestamps are absent, the worker uses wall-clock time around the Lambda SDK
call. If the SDK call throws, the elapsed call time is retained as the execution time.

For an ASYNC-lane invocation, `execution_time` is the duration from the returned lambda output. Missing
lambda timestamps produce `0`, meaning that execution duration is unavailable. The worker does not
substitute the interval from submission to SQS result delivery, because that interval includes queue
and delivery delay and is not Lambda execution time. That broader interval is represented by
`processing_time`.

Completed run results are also aggregated daily into `lambda_run_stat_day`, grouped by lambda version,
invocation lane, and result code. That table stores request and assignment counts plus average,
minimum, and maximum `execution_time`.

These persisted execution measurements are separate from `LambdaWorkloadMetrics`: they do not create a
`lambda.*` row and are not returned by `/man/lambdaworkload`. Adding live Lambda series would require
explicit Lambda operations and instrumentation at the synchronous invocation and asynchronous result
paths.

## Exposure and process boundaries

Both Spring Boot processes expose the same read-only Actuator endpoint ID, but each endpoint reads
only the static collector in its own JVM.

| Process | URL | Expected data |
| --- | --- | --- |
| API | `GET http://localhost:8087/man/lambdaworkload` | API-local annotated work, notably lambda-variable reads and writes and any lambda workflows executed by API requests. |
| Worker | `GET http://localhost:8083/man/lambdaworkload` | Worker-local lambda-run workflows, loading, state transitions, recovery scans, and result persistence. |

The management servers bind to `127.0.0.1`, the endpoint has read-only access, and other Actuator
endpoints are disabled by default. The worker also exposes its separately configured shutdown
endpoint.

There is no automatic cross-process aggregation. A complete view requires collecting both endpoint
responses and retaining the process identity. If several API or worker instances are running, every
instance must be scraped separately. Values from different processes can be summed for call and
failure counts, but latency averages require weighting by their corresponding call counts; maxima can
be compared directly. The `elapsed` values reveal how long each process-local series has existed.

Example response row:

```json
{
  "name": "sql.selectLambdaVariables",
  "elapsed": "2h17m",
  "avgCPM_1Hour": 3.5,
  "maxCPM_1Hour": 12,
  "avgCallTimeMicros_1Hour": 1840,
  "calls_1Hour": 210,
  "failures_1Hour": 2,
  "avgCPM_6Hours": 2.1,
  "maxCPM_6Hours": 12,
  "avgCallTimeMicros_6Hours": 1760,
  "calls_6Hours": 288,
  "failures_6Hours": 2,
  "avgCPM_3Days": 2.1,
  "maxCPM_3Days": 12,
  "avgCallTimeMicros_3Days": 1760,
  "calls_3Days": 288,
  "failures_3Days": 2
}
```

## Interpretation guidelines

- Use `workflow.*` to judge the user-visible cost of a complete state transition.
- Use `sql.*` to identify the database operation responsible for that cost.
- Compare failures with calls; a failure count alone does not show the failure rate.
- Compare `maxCPM` with `avgCPM` to distinguish bursts from sustained load.
- Treat `avgCallTimeMicros` as a mean. It can hide a small number of very slow calls.
- Do not sum nested workflow and SQL call durations as if they were independent work.
- Do not compare `execution_time` with `processing_time` as equivalent latency. The latter has a
  broader boundary, especially for asynchronous execution.
- Do not interpret an ASYNC `execution_time` of zero as a zero-duration Lambda run; it means the
  lambda output did not provide usable execution timestamps.

## Source locations

- Annotation and operation catalog:
  `core/src/main/java/dev/olegz/vf/core/dao/metric/LambdaWorkloadMetric.java`
- Aspect:
  `core/src/main/java/dev/olegz/vf/core/dao/metric/LambdaWorkloadMetricAspect.java`
- Collector:
  `core/src/main/java/dev/olegz/vf/core/dao/metric/LambdaWorkloadMetrics.java`
- Snapshot contract:
  `core/src/main/java/dev/olegz/vf/core/dao/metric/LambdaWorkloadMetricSnapshot.java`
- SQL call sites:
  `core/src/main/java/dev/olegz/vf/core/dao/mapper/LambdaRunMapper.java`
- Workflow call sites:
  `core/src/main/java/dev/olegz/vf/core/service/lambda/LambdaRunStateServiceImpl.java`
- API endpoint:
  `api/src/main/java/dev/olegz/vf/api/monitoring/LambdaWorkloadEndpoint.java`
- Worker endpoint:
  `worker/src/main/java/dev/olegz/vf/worker/monitoring/LambdaWorkloadEndpoint.java`
- Lambda invocation timing:
  `worker/src/main/java/dev/olegz/vf/worker/service/LambdaFunctionInvoker.java`
- Run-result timing persistence:
  `worker/src/main/java/dev/olegz/vf/worker/service/LambdaRunService.java`
