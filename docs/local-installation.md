# Local installation and deployment

This guide installs a complete local VF server: PostgreSQL, Kafka, the API process, and the worker
process. AWS integrations are switched to VF's local file-backed implementations; Docker is needed
only when lambda images are built or invoked locally.

Commands assume the repository root is the current directory.

## 1. Prerequisites

Install:

- JDK 25;
- Maven 3.9 or newer;
- PostgreSQL 11 or newer;
- Kafka 4.3.x in KRaft mode;
- Docker, if local lambda deployment/execution will be tested;
- `curl`, `openssl`, and the `psql` command-line client.

Verify the build tools:

```bash
java -version
mvn -version
```

## 2. Create the runtime home

VF does not read operational properties from the checkout at runtime. It requires a separate
runtime home with a readable `config/properties` directory.

```bash
export VF_HOME="$HOME/.myprojects/vf"
export VF_LOG_HOME="$VF_HOME/logs"

mkdir -p "$VF_HOME/config/properties" "$VF_LOG_HOME"
cp config/properties/*.properties "$VF_HOME/config/properties/"
```

Keep this directory outside version control. The copied files are configuration templates: replace
all required placeholder values and remove values for services that are not used. Do not commit
database passwords, AWS credentials, or the key-encryption key.

Create one stable 256-bit key-encryption key for this environment:

```bash
export VF_KEK="$(openssl rand -base64 32)"
```

Persist `VF_HOME`, `VF_LOG_HOME`, and the same `VF_KEK` in a secure local environment mechanism so
both the API and worker receive identical values after a restart. Changing `VF_KEK` makes existing
encrypted values and signing-key material unreadable.

## 3. Configure the database connection

Edit `$VF_HOME/config/properties/database.properties` and set at least:

```properties
jdbc.url=jdbc:postgresql://127.0.0.1:5432/vf
jdbc.user=<local database user>
jdbc.password=<local database password>
jdbc.timeout=<optional JDBC URL suffix, or empty>
jdbc.maxActiveConnections=20
```

Restrict the file because it contains a secret:

```bash
chmod 600 "$VF_HOME/config/properties/database.properties"
```

Do not put the password on a command line. Use a protected PostgreSQL password file or another
supported secure authentication mechanism. On a fresh PostgreSQL server, apply the scripts in this
order:

```bash
psql -f config/database/postgresql/config/create_database.sql
psql -f config/database/postgresql/config/tx_isolation.sql
psql -f config/database/postgresql/ddl/create_tables.sql
psql -f config/database/postgresql/ddl/create_functions.sql
psql -f config/database/postgresql/ddl/create_views.sql
psql -f config/database/postgresql/dml/init_data.sql
```

The server must use UTF-8 and `READ COMMITTED`. The canonical settings and instructions are in
[`config/database/README.md`](../config/database/README.md). Do not apply
`ddl/test_constraints.sql` to a non-test database.

Existing databases are different: review and apply the applicable scripts in
`config/database/postgresql/migrations` before starting new application code. VF does not run migrations
automatically.

## 4. Configure local AWS substitutes

Set local mode in `$VF_HOME/config/properties/local.properties`:

```properties
vf.aws.local=true
vf.aws.local.root=/absolute/path/to/vf-local-aws
vf.messaging.mock=false
```

With `vf.aws.local=true`, VF uses local implementations instead of real AWS credentials and stores
objects and logs below `vf.aws.local.root`. Create that directory:

```bash
mkdir -p "$VF_HOME/aws"
```

Using `$VF_HOME/aws` as the configured absolute path is the conventional choice. Leave
`vf.messaging.mock=false` for a two-process deployment: the in-memory broker is confined to one JVM
and cannot connect API and worker.

For API, report, and non-invocation worker smoke tests, the file-backed AWS clients need no external
service. Local lambda build and invocation additionally require Docker and the Lambda Runtime Interface
Emulator (RIE). Set `vf.aws.local.lambda.rie` to the absolute RIE executable path. Optional local
Docker, handler, entrypoint, and port-range settings are documented by their comments in
`config/properties/local.properties` and defaults in the local AWS implementation.

The bucket and queue names in `aws.properties` are logical names even in local mode. Supply nonempty,
local-only names for source code, logs, report output, and lambda results. Real AWS access keys and an
IAM execution role are not required in local mode.

## 5. Install and configure Kafka

Set `KAFKA_HOME` to an unpacked Kafka 4.3.x distribution, then copy the project configuration:

```bash
export KAFKA_HOME="/absolute/path/to/kafka_2.13-4.3.x"
mkdir -p "$VF_HOME/kafka"
cp config/kafka/server.properties "$VF_HOME/kafka/server.properties"
cp -R config/kafka/bin "$VF_HOME/kafka/"
```

Edit `$VF_HOME/kafka/server.properties` before formatting. In particular, change `log.dirs` to an
absolute directory inside this runtime home; the checked-in example contains a machine-specific
path and must not be used unchanged.

```properties
log.dirs=/absolute/path/to/.myprojects/vf/kafka/log-dirs
```

Configure the application bootstrap address in
`$VF_HOME/config/properties/messaging.properties`:

```properties
vf.kafka.servers=127.0.0.1:9092
```

Format a new KRaft data directory exactly once, then start Kafka and create the VF topics:

```bash
"$VF_HOME/kafka/bin/create_cluster.sh"
"$VF_HOME/kafka/bin/start_kafka.sh"
"$VF_HOME/kafka/bin/create_topics.sh"
```

Do not rerun `create_cluster.sh` against an already formatted `log.dirs`. Topic auto-creation is
disabled, so `create_topics.sh` is required. If a topic already exists, Kafka will report that fact;
do not repeatedly recreate a working cluster.

## 6. Complete the application properties

Review every copied file under `$VF_HOME/config/properties`. For a local full server, the important
settings are:

| File | Required local values |
| --- | --- |
| `database.properties` | `jdbc.url`, `jdbc.user`, `jdbc.password` |
| `messaging.properties` | `vf.kafka.servers=127.0.0.1:9092` |
| `local.properties` | `vf.aws.local=true`, `vf.messaging.mock=false`, optional local Lambda settings |
| `services.properties` | `vf.host.api=http://127.0.0.1:8086` |
| `aws.properties` | local logical bucket/queue names and a region such as `us-east-1` |
| `lambda.properties` | architecture and a local build command if lambda deployment will be exercised |

Property files are watched after startup, but settings captured by static initialization or used to
construct clients should be treated as restart-required. Restart both JVMs after changing local
mode, broker endpoints, database settings, encryption, or AWS client configuration.

## 7. Build the project

Build and install every module into the local Maven repository:

```bash
mvn clean install
```

The deployable Spring Boot artifacts are:

```text
api/target/api.jar
worker/target/worker.jar
```

For a faster build that still creates the artifacts but skips tests:

```bash
mvn clean install -DskipTests
```

## 8. Register reports

Report definitions are external deployment inputs, so building the JAR does not install them.
Validate them without a database write:

```bash
mvn -q -pl report -am -DskipTests -Pregister-reports verify \
  -Dexec.args="--definitions report/database/definitions --validate-only"
```

Then register them using the database configured under `VF_HOME`:

```bash
mvn -q -pl report -am -DskipTests -Pregister-reports verify \
  -Dexec.args="--definitions report/database/definitions"
```

If the initial report groups and schedule are wanted, apply the three scripts listed in
[`report/database/README.md`](../report/database/README.md) after registration. They are one-time
setup scripts and are not idempotent.

## 9. Create the local administrator

The seed data creates `admin@demo.org` without a usable password. Set one interactively so it is not
placed in shell history:

```bash
core/bin/reset-password.sh --username admin@demo.org
```

For a non-seeded organization, create an administrator with:

```bash
core/bin/create-admin.sh --org <organization-id> --username <username>
```

Both tools use the same `VF_HOME` database settings and require the preceding Maven install.

## 10. Start the server

Open two terminals with the same `VF_HOME`, `VF_LOG_HOME`, `VF_KEK`, and `KAFKA_HOME` environment.
Start the worker first:

```bash
java -jar worker/target/worker.jar
```

Then start the API:

```bash
java -jar api/target/api.jar
```

Expected local ports are:

| Process | Application | Management |
| --- | ---: | ---: |
| Worker | `8082` | `127.0.0.1:8083` |
| API | `8086` | `127.0.0.1:8087` |

The API includes `/vf/reports`; do not also start the standalone report application on the same
HTTP port. To run only the report service, stop the API and use:

```bash
mvn -pl report spring-boot:run
```

## 11. Verify the deployment

Check that:

1. Kafka is listening on `127.0.0.1:9092` and all topics were created.
2. Worker logs reach `Application started` without database, Kafka, or local-AWS initialization
   errors.
3. API logs reach `Application started` and port `8086` accepts connections.
4. Authentication for `admin@demo.org` returns a normal VF response envelope.
5. A report list request succeeds after report registration and group assignment.

The public REST contract is in `api/doc/openapi.yaml`, and the ready-to-import request collection is
`postman/VF.postman_collection.json`. Lambda workload diagnostics are available locally at:

```text
GET http://127.0.0.1:8083/man/lambdaworkload   # worker
GET http://127.0.0.1:8087/man/lambdaworkload   # API
```

These endpoints describe current in-process work; they are not general health or durable queue-depth
endpoints.

## 12. Stop and restart

Stop the API with the terminal signal. The worker also exposes a loopback-only graceful shutdown:

```bash
curl -X POST http://127.0.0.1:8083/man/shutdown
```

Stop Kafka with:

```bash
"$VF_HOME/kafka/bin/stop_kafka.sh"
```

Keep the PostgreSQL database, Kafka `log.dirs`, `$VF_HOME/aws`, and the stable `VF_KEK` between restarts.
Reformat Kafka or rebuild the database only when a clean environment is intentionally required.

## Common startup failures

| Symptom | Likely cause |
| --- | --- |
| `VF_HOME is not set` or missing properties directory | The environment was not exported in that terminal, or `$VF_HOME/config/properties` does not exist. |
| Database connection failure | PostgreSQL is stopped, JDBC settings are wrong, or the `vf` database has not been created. |
| Missing table/column | Fresh DDL or a required manual migration was not applied before the new code started. |
| Kafka connection or unknown-topic errors | Kafka is stopped, `vf.kafka.servers` is wrong, or `create_topics.sh` was not run. |
| Real AWS credential errors in a local run | `vf.aws.local=true` was not loaded before AWS classes initialized; correct it and restart. |
| Local lambda invocation cannot start | Docker is unavailable or `vf.aws.local.lambda.rie` does not point to an executable RIE binary. |
| Reports list is empty | Definitions were validated but not registered, or report groups were not assigned. |
| Old/duplicate report definitions appear during development | Clean the report build output and rerun registration validation. |

For database details use [`config/database/README.md`](../config/database/README.md); for report
deployment use [`report/database/README.md`](../report/database/README.md); for runtime behavior use
[Architecture and Processes](architecture-and-processes.md).
