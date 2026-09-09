# PostgreSQL database setup

Scripts and configuration for provisioning the `vf` PostgreSQL database used by Vigilo Framework.
They are applied manually by a DBA or developer; application startup does not create or migrate the
schema.

## Layout

| Path | Purpose |
| --- | --- |
| `postgresql/config/` | Database creation and server/isolation settings |
| `postgresql/ddl/` | Tables, functions, views, and test-only constraints |
| `postgresql/dml/` | Seed data |
| `postgresql/migrations/` | Manual updates for databases created from older DDL |

## Fresh database

PostgreSQL 11 or newer is required for the declarative partitioning used by the schema. Append
`postgresql/config/postgresql.conf` to the server configuration (or include it through `conf.d`),
then reload or restart PostgreSQL. It pins UTF-8 client encoding and `READ COMMITTED` transaction
isolation.

Run the following with `psql` as a role allowed to create databases and schema objects:

```sql
\i postgresql/config/create_database.sql
\i postgresql/config/tx_isolation.sql
\i postgresql/ddl/create_tables.sql
\i postgresql/ddl/create_functions.sql
\i postgresql/ddl/create_views.sql
\i postgresql/dml/init_data.sql
```

The DDL and DML scripts connect to `vf` themselves. The seed data inserts explicit identity values
and then advances the corresponding sequences with `setval(...)`.

## Test-only constraints

`postgresql/ddl/test_constraints.sql` adds foreign keys used on test servers. Do not apply it to
production.

## Schema changes

Edit the canonical PostgreSQL DDL directly for fresh installations. When an existing database must
be upgraded in place, add and manually apply a matching script under `postgresql/migrations/` before
starting application code that depends on the change. VF does not discover or execute migrations at
build time or startup.

The canonical schema uses PostgreSQL `boolean` for logical flags, `jsonb` for structured JSON, and
`uuid` for identifiers generated internally as UUIDs. Apply
`postgresql/migrations/20260902_use_native_boolean_jsonb_uuid_types.sql` when upgrading a database
created before those native types were introduced. The migration intentionally fails on malformed
non-empty JSON or UUID values so they can be corrected explicitly. Its `ALTER COLUMN TYPE`
operations take table locks and may rewrite stored rows, so apply it during a maintenance window
appropriate for the size of the affected tables and partitions.

For older installations, apply these historical migrations in order:

- `postgresql/migrations/20260903_rename_bot_active_versions_latest_status.sql` renames
  `bot_active_versions.is_latest` to `latest_status`, preserving the exact unique key
  `(bot_id, latest_status)`.
- `postgresql/migrations/20260904_rename_bot_input_messages_pending_inputs.sql` renames the old
  pending-input tables, partitions, indexes, and columns in place.

These scripts require exclusive table locks. Historical migration scripts keep their original
names and SQL; fresh installations use only the current canonical DDL.
