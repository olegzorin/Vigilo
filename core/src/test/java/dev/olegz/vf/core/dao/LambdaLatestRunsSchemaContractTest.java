package dev.olegz.vf.core.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class LambdaLatestRunsSchemaContractTest {

    @Test
    void canonicalSchemaAndMigrationUseOnePhysicalTable() throws IOException {
        String postgresqlDdl = Files.readString(databaseFile("ddl/create_tables.sql"));
        assertFalse(postgresqlDdl.contains("PARTITION BY HASH (lambda_assignment_id)"));
        assertFalse(postgresqlDdl.contains("CREATE TABLE lambda_latest_runs_p0"));

        String postgresqlMigration = Files.readString(databaseFile(
            "migrations/20260902_remove_bot_latest_runs_partitioning.sql"));
        assertTrue(postgresqlMigration.contains("LOCK TABLE bot_latest_runs IN ACCESS EXCLUSIVE MODE"));
        assertTrue(postgresqlMigration.contains("INSERT INTO bot_latest_runs_unpartitioned"));
        assertTrue(postgresqlMigration.contains("RENAME TO bot_latest_runs"));
    }

    private static Path databaseFile(String suffix) {
        String relative = "config/database/postgresql/" + suffix;
        return Stream.of(Path.of(relative), Path.of("..", relative))
            .filter(Files::isRegularFile)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Database file not found: " + relative));
    }
}
