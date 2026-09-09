# Report definitions

Each `definitions/*.report.yaml` file contains report metadata. Its `query` property points to a
separate executable SQL file below the same directory. Parameter indexes are their zero-based YAML
list positions; field indexes are their one-based list positions. These files are deployment inputs
kept outside `src/main/resources`, so they are not packaged in the report JAR. Run the commands
below from the repository root.

Validate all definitions without connecting to the database:

```bash
mvn -q -pl report -am -DskipTests -Pregister-reports verify \
  -Dexec.args="--definitions report/database/definitions --validate-only"
```

Register all reports using the standard `VF_HOME` JDBC properties:

```bash
mvn -q -pl report -am -DskipTests -Pregister-reports verify \
  -Dexec.args="--definitions report/database/definitions"
```

The deployment replaces report parameters, fields, and metadata in one transaction. Report-group
membership is deliberately outside report definitions and is never changed by this command.

## Initial report-group setup

The SQL files in `setup/` provide the initial report groups, memberships, and schedules. They use SQL
supported by PostgreSQL. After creating the database schema and registering the report
definitions, execute the scripts with the database's SQL client in this order:

1. `setup/create_report_groups.sql`
2. `setup/assign_report_groups.sql`
3. `setup/create_report_schedules.sql`

The first script creates the predefined groups. The second assigns the registered reports to those
groups, so the referenced reports and groups must already exist. The third schedules report 2 in
group 2 for 07:30 UTC every Friday. These scripts contain plain `INSERT` statements and are intended
for one-time setup; running them again without first removing the existing rows will cause primary-key
or unique-key conflicts. Keep report-group assignments in `setup/assign_report_groups.sql` and apply
the setup scripts through the database's SQL client.
