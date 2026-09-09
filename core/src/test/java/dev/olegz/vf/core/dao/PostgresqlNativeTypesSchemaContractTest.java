package dev.olegz.vf.core.dao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresqlNativeTypesSchemaContractTest {
    private static final String MIGRATION =
        "migrations/20260902_use_native_boolean_jsonb_uuid_types.sql";

    @Test
    void canonicalSchemaUsesNativeBooleanJsonbAndInternalUuidTypes() throws IOException {
        String ddl = Files.readString(databaseFile("ddl/create_tables.sql"));

        assertContains(tableDefinition(ddl, "lambda_active_versions"), "is_public               boolean");
        assertContains(tableDefinition(ddl, "lambda_assignments"), "is_testing           boolean");
        assertContains(tableDefinition(ddl, "lambda_latest_runs"), "is_started          boolean");
        assertContains(tableDefinition(ddl, "lambda_pending_inputs_p"), "is_large_input      boolean");
        assertContains(tableDefinition(ddl, "report_params"), "is_required         boolean");
        assertContains(tableDefinition(ddl, "report_execution_history"), "on_demand       boolean");

        assertContains(tableDefinition(ddl, "device_current_states"), "current_state         jsonb");
        assertContains(tableDefinition(ddl, "lambdas"), "metadata            jsonb");
        assertContains(tableDefinition(ddl, "lambda_versions"), "schedule            jsonb");
        assertContains(tableDefinition(ddl, "lambda_active_versions"), "schedule                jsonb");
        assertContains(tableDefinition(ddl, "lambda_alerts"), "changes_json        jsonb");
        assertContains(tableDefinition(ddl, "report_execution_history"), "metadata        jsonb");
        assertContains(tableDefinition(ddl, "report_group_schedules"), "parameters          jsonb");

        assertContains(tableDefinition(ddl, "lambda_alerts"), "alert_id            uuid");
        assertContains(tableDefinition(ddl, "cron_job_locks"), "owner_id            uuid");
        assertContains(tableDefinition(ddl, "lambda_invoke_retry_outbox"), "claim_id            uuid");
        assertContains(tableDefinition(ddl, "lambda_async_submission_outbox"), "claim_id            uuid");
        assertContains(tableDefinition(ddl, "lambda_run_completion_outbox"), "claim_id            uuid");
        assertContains(tableDefinition(ddl, "lambda_reset_outbox"), "event_id            uuid");
        assertContains(tableDefinition(ddl, "lambda_reset_outbox"), "claim_id            uuid");
        assertContains(tableDefinition(ddl, "cache_invalidation_outbox"), "claim_id      uuid");
    }

    @Test
    void inPlaceMigrationConvertsEveryNativeTypeFamily() throws IOException {
        String migration = Files.readString(databaseFile(MIGRATION));
        assertTrue(migration.contains("TYPE boolean USING"));
        assertTrue(migration.contains("TYPE jsonb"));
        assertTrue(migration.contains("TYPE uuid USING"));
        assertTrue(migration.contains("ALTER COLUMN parameters SET DEFAULT '{}'::jsonb"));
        assertTrue(migration.contains("ADD CONSTRAINT chk_public_latest"));
    }

    private static void assertContains(String table, String declaration) {
        assertTrue(table.contains(declaration), () -> "Missing declaration: " + declaration);
    }

    private static Path databaseFile(String suffix) {
        String relative = "config/database/postgresql/" + suffix;
        return Stream.of(Path.of(relative), Path.of("..", relative))
            .filter(Files::isRegularFile)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Database file not found: " + relative));
    }

    private static String tableDefinition(String ddl, String table) {
        int start = ddl.indexOf("CREATE TABLE " + table);
        if (start < 0) throw new IllegalStateException("Table not found: " + table);
        int end = ddl.indexOf("\n);", start);
        if (end < 0) throw new IllegalStateException("Table definition is incomplete: " + table);
        return ddl.substring(start, end);
    }
}
