package dev.olegz.vf.core.dao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LambdaActiveVersionsSchemaContractTest {
    private static final String MIGRATION =
        "migrations/20260903_rename_bot_active_versions_latest_status.sql";

    @Test
    void canonicalSchemaUsesLatestStatusAndRequestedUniqueKey() throws IOException {
        String ddl = Files.readString(databaseFile("ddl/create_tables.sql"));
        String table = tableDefinition(ddl, "lambda_active_versions");

        assertTrue(table.contains("latest_status           smallint not null"));
        assertFalse(table.contains("is_latest"));
        assertTrue(ddl.contains("ADD CONSTRAINT uk_lambda_active_versions_latest_status\n" +
            "        UNIQUE (lambda_id, latest_status)"));
        assertTrue(ddl.contains("ADD CONSTRAINT chk_public_latest_status"));
    }

    @Test
    void inPlaceMigrationRenamesColumnAndReplacesUniqueIndex() throws IOException {
        String migration = Files.readString(databaseFile(MIGRATION));

        assertTrue(migration.contains("DROP INDEX ui_bot_active_versions_latest"));
        assertTrue(migration.contains("RENAME COLUMN is_latest TO latest_status"));
        assertTrue(migration.contains("RENAME CONSTRAINT chk_public_latest TO chk_public_latest_status"));
        assertTrue(migration.contains("ADD CONSTRAINT uk_bot_active_versions_latest_status\n" +
            "        UNIQUE (bot_id, latest_status)"));
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
