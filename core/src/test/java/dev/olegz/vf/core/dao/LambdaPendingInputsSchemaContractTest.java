package dev.olegz.vf.core.dao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LambdaPendingInputsSchemaContractTest {
    private static final String MIGRATION =
        "migrations/20260904_rename_bot_input_messages_pending_inputs.sql";

    @Test
    void canonicalSchemaUsesPendingInputNames() throws IOException {
        String ddl = Files.readString(databaseFile("ddl/create_tables.sql"));
        String latestRuns = tableDefinition(ddl, "lambda_latest_runs");
        String pendingInputs = tableDefinition(ddl, "lambda_pending_inputs_p");

        assertTrue(latestRuns.contains("next_input_number     bigint"));
        assertFalse(latestRuns.contains("message_number_offset"));
        assertTrue(pendingInputs.contains("input_number        bigint"));
        assertTrue(pendingInputs.contains("is_large_input      boolean"));
        assertTrue(pendingInputs.contains("input_size          int"));
        assertTrue(pendingInputs.contains("input_data          bytea"));
        assertTrue(pendingInputs.contains("large_input_data    bytea"));
        assertFalse(ddl.contains("CREATE TABLE lambda_input_messages_p"));
    }

    @Test
    void inPlaceMigrationRenamesTablesColumnsAndIndexes() throws IOException {
        String migration = Files.readString(databaseFile(MIGRATION));

        assertTrue(migration.contains("RENAME COLUMN message_number_offset TO next_input_number"));
        assertTrue(migration.contains("RENAME COLUMN message_number TO input_number"));
        assertTrue(migration.contains("RENAME COLUMN is_large_message TO is_large_input"));
        assertTrue(migration.contains("RENAME COLUMN message_size TO input_size"));
        assertTrue(migration.contains("RENAME COLUMN message_body TO input_data"));
        assertTrue(migration.contains("RENAME COLUMN large_message_body TO large_input_data"));
        assertTrue(migration.contains("ALTER TABLE bot_input_messages_p RENAME TO bot_pending_inputs_p"));
        assertTrue(migration.contains(
            "ALTER INDEX i_bot_input_messages_p_bot_assignment RENAME TO i_bot_pending_inputs_p_bot_assignment"));
        assertTrue(migration.contains(
            "ALTER INDEX bot_input_messages_p_p3_bot_assignment_id_idx RENAME TO bot_pending_inputs_p_p3_bot_assignment_id_idx"));
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
