package dev.olegz.vf.core.dao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LambdaAlertSchemaContractTest {

    @Test
    void canonicalSchemaAndMigrationContainAlertIdempotencyAndLifecycle() throws IOException {
        String ddl = Files.readString(databaseFile("ddl/create_tables.sql"));
        String table = tableDefinition(ddl, "lambda_alerts");
        assertTrue(table.contains("idempotency_key"));
        assertTrue(table.contains("payload_hash"));
        assertTrue(table.contains("organization_id"));
        assertTrue(table.contains("lambda_assignment_id"));
        assertTrue(table.contains("changes_json"));
        assertTrue(table.contains("alert_id            uuid"));
        assertTrue(table.contains("changes_json        jsonb"));
        assertTrue(table.contains("acknowledged_at"));
        assertTrue(table.contains("resolved_at"));
        assertTrue(ddl.contains("ui_lambda_alerts_idempotency"));

        String migration = Files.readString(databaseFile("migrations/20260821_create_bot_alerts.sql"));
        assertTrue(migration.contains("CREATE TABLE bot_alerts"));
        assertTrue(migration.contains("ui_bot_alerts_idempotency"));
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
