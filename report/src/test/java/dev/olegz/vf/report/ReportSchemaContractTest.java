package dev.olegz.vf.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class ReportSchemaContractTest {
    @Test
    void removedReportGroupColumnsStayOutOfCanonicalSchema() throws IOException {
            String ddl = Files.readString(schema());

            String reports = tableDefinition(ddl, "reports");
            assertFalse(reports.contains("access"));
            assertFalse(reports.contains("sort_order"));

            String reportGroups = tableDefinition(ddl, "report_groups");
            assertFalse(reportGroups.contains("dashboard"));
            assertFalse(reportGroups.contains("dashboard_name"));

            String reportGroupReports = tableDefinition(ddl, "report_group_reports");
            assertFalse(reportGroupReports.contains("access"));
            assertFalse(reportGroupReports.contains("sort_order"));

            String reportGroupOrganizations = tableDefinition(ddl, "report_group_organizations");
            assertFalse(reportGroupOrganizations.contains("notification_category"));
            assertFalse(reportGroupOrganizations.contains("start_date"));
            assertTrue(reportGroupOrganizations.contains("assigned_at"));
    }

    private static Path schema() {
        String relative = "config/database/postgresql/ddl/create_tables.sql";
        return Stream.of(Path.of(relative), Path.of("..", relative))
            .filter(Files::isRegularFile)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Canonical schema not found: " + relative));
    }

    private static String tableDefinition(String ddl, String table) {
        int start = ddl.indexOf("CREATE TABLE " + table);
        if (start < 0) throw new IllegalStateException("Table not found: " + table);
        int end = ddl.indexOf("\n);", start);
        if (end < 0) throw new IllegalStateException("Table definition is incomplete: " + table);
        return ddl.substring(start, end);
    }
}
