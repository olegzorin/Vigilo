# Report module implementation

The `report` module is a self-contained, database-driven reporting subsystem extracted
from the CareDaily server. It owns the report model, persistence, execution, REST API,
definition-registration tools, and a standalone Spring Boot entry point.

## Module boundary and wiring

The module depends on `foundation` and `registry`, but not on `core`, AWS, or messaging.
`ReportConfig` enables the service, executor, DAO, and MyBatis mapper. The optional REST
adapter is enabled separately through `ReportRestConfig`.

The main `api` application imports both configurations. The module also provides
`dev.olegz.vf.report.Application`, which imports registry authentication and database
configuration and can run the report REST service independently.

The `worker` application depends on the module and imports `ReportConfig` to execute
scheduled reports. Scheduler ownership remains in `worker`; the report module does not
depend on the worker or its scheduler implementation.

The standalone application scans only the report REST package. API-owned components,
such as the API exception handler and API-specific web configuration, are therefore not
automatically included in standalone mode.

## Dynamic report registration

Reports are data-driven rather than implemented as individual Java classes. A definition
consists of a YAML document describing the report, parameters, fields, and metadata, plus
a separate SQL file containing the query.

New reports can be added without rebuilding or restarting the module:

1. Add the YAML and SQL definition files under `../report/database/definitions` or another filesystem directory.
2. Run `RegisterReportsTool` with `--definitions /path/to/definitions`.
3. Add the report-group assignment to `../report/database/setup/assign_report_groups.sql` and apply the
   setup scripts.
4. The running application discovers the report through its normal database queries.

Registration is explicit rather than directory-watched. The registration tool validates
all definitions and transactionally upserts the report records, replacing the associated
parameters, fields, and metadata. A rebuild is needed only when a report requires Java
behavior outside the existing SQL/YAML execution model.

The repository currently includes eight deployment definitions under
`../report/database/definitions`. They live outside `src/main/resources`, so they are not
packaged in the report JAR. The registration tool reads them directly from the filesystem
and can instead operate on any external definition directory.

## SQL execution model

The full report query is loaded from the database and inserted into the MyBatis mapper as
trusted SQL. Runtime values remain bound through the fixed `#{p0}` through `#{p9}`
parameter placeholders.

The definition loader:

- permits at most ten parameters;
- requires each query segment to start with `SELECT` or `WITH`;
- rejects unknown YAML properties and duplicate names or columns;
- requires the definition filename to begin with the report ID;
- ensures referenced SQL files remain inside the selected definition directory.

Report SQL may contain multiple query segments separated by `;;;`. Each segment executes
separately, but writes its returned values starting at row zero. This supports reports in
which separate one-row queries populate different columns of one output row. The recorded
row count is the sum of rows returned by all segments and therefore need not equal the
number of rows in the final CSV.

SQL may also contain `$J{...$J}` expressions. They are evaluated using a strict,
package-restricted JEXL engine before the query is passed to MyBatis. Since the result of
an expression becomes SQL text, ordinary values should continue to use bound MyBatis
parameters.

## Fixed result-column convention

Report queries return rows through the legacy `ReportData` structure. SQL columns must be
aliased using its fixed vocabulary:

- `id`, `name`, and `description`;
- strings `s1` through `s20`;
- numbers `n1` through `n40`;
- timestamps `t1` through `t20`;
- booleans `b1` through `b10`.

The YAML field declarations map those aliases to typed, user-facing fields. The executor
uses that mapping to populate the report and generate CSV output.

## Execution and output persistence

On-demand execution performs the following steps:

1. Verify that the requested report is available through a report group.
2. Parse and bind the declared parameters.
3. Execute the SQL segments and populate the declared fields.
4. Generate a UTF-8 CSV with headers.
5. Package the CSV in a ZIP archive.
6. Store the ZIP and execution metadata in `report_execution_history`.

Compressed ZIPs up to 64,000 bytes remain database-backed. Larger ZIPs are uploaded to the
configured reports S3 bucket under the internal object ID, and the execution-history row is
stored without a BLOB. The public download adapter accepts a one-day signed JWT containing that
object ID; it reads the database BLOB first and falls back to the same S3 key.
Execution exceptions are likewise persisted as error metadata rather than preventing the
execution-history record from being created.

Scheduled summary reports use the same SQL, CSV, ZIP, size-limit, metadata, and error
persistence path. Scheduled analytic reports store headerless CSV in `analytic_output`
instead of creating a ZIP or object ID. Every scheduled attempt is associated with its
schedule and scheduled fire time in `report_execution_history`.

On-demand executions have a fixed retention period of 24 hours, but the module currently
contains no runtime caller for the cleanup operation.

## Authentication, tenancy, and report groups

Every report REST endpoint requires the `API_KEY` header. The report-specific action
context resolves the key to a registry user.

Organization-specific reads and executions require the requested organization to match
the caller's organization. Assigning a report group requires the caller to be the target
organization's administrator or the administrator of one of its ancestors. Removing a
report group requires the caller to be the target organization's administrator.

Report availability is controlled by report groups. Organization-specific group
assignments are inherited through `organizations_hierarchy`. Assigning a group to a
parent organization removes explicit assignments for its descendants, preventing
overlapping inherited assignments. Assignment is idempotent when the target organization
or one of its ancestors already has the group.

## Scheduling support

Schedules are stored in `report_group_schedules`. Each row identifies one report and report
group and contains JSON parameters, an IANA timezone (UTC by default), a six-field Quartz
cron expression, and the last and next execution timestamps. The unique `(report_id,
report_group_id)` key permits one schedule per report-group membership.

The worker owns the static `RunScheduledReportsJob`. Spring discovers it as a `CronJob`, and
`ExecutorCronScheduler` runs it once a day at 08:00 UTC with its normal non-concurrent,
database-backed job lock. The job calls `ReportsService.runScheduledReports()` and logs the
number of report executions it produced.

Scheduled execution proceeds as follows:

1. Select schedule rows whose `next_execution_date` is null or no later than the current time.
2. Load and validate the report parameters and cron expression for each due schedule.
3. Execute a system schedule once, or evaluate an organizational schedule for every organization
   assigned to its report group. Organizational reports must declare an `organizationId`
   parameter; the service binds the current organization and evaluates the cron in that
   organization's timezone, falling back to the schedule timezone.
4. Execute the report outside a database transaction so long-running report SQL does not hold
   schedule-row locks.
5. In one short transaction, lock and revalidate the schedule, persist every execution or execution
   error in `report_execution_history`, and advance `last_execution_date` and
   `next_execution_date`. If the schedule changed while the report ran, discard the prepared result.

The short transaction prevents history from committing without the matching schedule advancement.
The schedule-row revalidation prevents another worker from committing a result prepared from the
same schedule occurrence after the first worker advances it.

One invalid schedule is logged without preventing other due schedules from running. A new
schedule with no `next_execution_date` is due on the next worker poll; after that first processing,
the cron expression controls its next timestamp. Because the static poll occurs only once per day,
a normal recurring fire must be due by 08:00 UTC to run on that day's poll. A later fire remains
due until the following day.

`../report/database/setup/create_report_schedules.sql` supplies the initial schedule for report 2
in organizational group 2. It stores `{"organizationId":"1"}` and fires at 07:30 UTC every
Friday (`0 30 7 ? * FRI`), so the Friday occurrence is due when the worker polls at 08:00 UTC.
The setup scripts create groups, assign reports, and then create schedules in that order.

The REST API can read scheduled execution history. The `send_to` schedule column is retained in
the schema, but scheduled email delivery is not implemented because email/template delivery was
excluded from this module.

## Intentional exclusions

The extraction intentionally excludes:

- report collections;
- organization-survey reports;
- shared/multi-cloud report aggregation;
- email and template-based delivery;
- Quartz scheduler adapters;
- the device-process report listener.

Canonical PostgreSQL report schema objects live under `config/database/postgresql/ddl`.
They are maintained as final DDL definitions and
are not applied automatically by the report module.
