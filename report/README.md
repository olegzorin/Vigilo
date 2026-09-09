# VF report module

This module contains the reusable report model, MyBatis persistence layer, CSV/analytic
output support, service layer, report SQL definitions and database migration resources
extracted from the CareDaily server project. Report collections are not supported.

This module owns and exposes the imported report REST surface under `/vf/reports`; the
VF `api` application activates it by importing `ReportRestConfig`.

`dev.olegz.vf.report.Application` is the standalone Spring Boot entry point. Run it with
`mvn -pl report spring-boot:run` after configuring the normal VF properties and database.
Organization-survey report resources are excluded. Shared-report aggregation, email delivery,
Quartz jobs, and the device-process message listener are not included.

Generated report ZIPs up to 64,000 bytes are stored in `report_execution_history.output`. Larger
ZIPs are uploaded to the S3 bucket configured by `vf.aws.s3.reportsBucket`, using the execution
object ID as the S3 key, and the history row retains no BLOB. Downloads use one-day signed JWT
URLs and read the database BLOB first, then S3 using that same object ID.

Import `ReportConfig` alongside the core `DataSourceConfig` to register the report service,
mapper, and DAO layers. Web applications additionally import `ReportRestConfig`. The
canonical PostgreSQL table, function, and view definitions are maintained under
`config/database/postgresql/ddl` and are not applied automatically.
