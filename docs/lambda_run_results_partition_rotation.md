# `lambda_run_results` Partition Rotation

`lambda_run_results` uses a fixed daily ring of PostgreSQL LIST partitions. It does not create and drop date-named partitions.

## Partition layout

The table contains a required numeric `part` column and eight physical partitions, `p0` through `p7`:

```sql
PARTITION BY LIST (part);
CREATE TABLE lambda_run_results_p0 PARTITION OF lambda_run_results FOR VALUES IN (0);
-- ... p1 through p7 follow the same pattern.
```

The schema is defined in `../config/database/postgresql/ddl/create_tables.sql`.

The default configuration is:

```properties
vf.db.lambda_run_results.partitions=8
vf.db.lambda_run_results.partShift=0
```

The configured count and shift must correspond to the partitions that physically exist in PostgreSQL.

## Assigning executions to partitions

Before an execution is inserted, `LambdaRunInfo.prepareFields()` derives `part` from its `requestDate`:

```text
part = UTC day number since 2026 % partitionCount + partitionShift
```

All executions from the same UTC calendar day therefore enter the same partition. Consecutive days move through the ring, and a partition number is reused after `partitionCount` days.

The calculation is implemented by `LambdaRunInfo.partitionIndex()`.

## Daily rotation

The daily workflow is implemented by `RotateLambdaRunResultsPartitions`:

1. Calculate the partition assigned to tomorrow.
2. Empty it with:

   ```sql
   TRUNCATE TABLE lambda_run_results_pN;
   ```

3. Determine which completed days have not yet been summarized, 
4. based on the latest date in `lambda_run_stat_day`.
5. Aggregate raw executions through yesterday into `lambda_run_stat_day`.
6. Collect and store the corresponding daily metrics.

Tomorrow's slot is selected because it is not used by today's writes. 
With eight partitions, that slot currently holds data from seven days ago. 
After the next UTC midnight, new executions begin filling the emptied slot.

This produces a raw-data retention window of approximately seven to eight 
calendar days, depending on whether the daily truncation has already run. 
Older execution history is available only through the daily aggregate 
rather than the original execution rows.

## Query partition pruning

Execution-history queries calculate the partitions intersecting 
the requested time interval and use both conditions:

```sql
s.request_date >= :startDate
AND s.request_date < :endDate
AND s.part IN (...)
```

The `part` predicate lets PostgreSQL prune unrelated partitions.
The exact date predicates distinguish the requested dates 
from older dates that previously occupied the same ring slots.

The query is defined in `LambdaStatisticsMapper.xml:37`.

## Scheduler status

`RotateLambdaRunResultsPartitions` is registered as a static `CronJob`. The scheduler uses the
job's default `disallowConcurrent()` value of `true`, preventing overlapping executions.
The job is triggered daily at `09:00`.

The job explicitly returns the framework's default UTC time zone. Partition assignment itself
also uses UTC days.

## Failure and catch-up behavior

Rotation truncates tomorrow's partition before performing catch-up aggregation. 
Under normal daily operation this is safe because yesterday occupies a different slot.

If aggregation falls behind by roughly the raw retention window, however, 
the partition selected for truncation can contain the oldest still-unaggregated day. 
That data would be removed before catch-up aggregation reads it. 
Operational monitoring should therefore detect missed aggregation runs before the ring wraps.

Exceptions from partition maintenance and statistics aggregation are caught and
logged independently by `RotateLambdaRunResultsPartitions`, allowing one part of the job
to run when the other fails.
