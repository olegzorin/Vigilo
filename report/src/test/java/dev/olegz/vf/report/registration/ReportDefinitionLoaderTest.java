package dev.olegz.vf.report.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.olegz.vf.report.ReportTestPaths;

class ReportDefinitionLoaderTest {
    @Test
    void loadsAllVersionedReportDefinitions() throws Exception {
        Path definitions = ReportTestPaths.definitions();

        List<LoadedReportDefinition> reports = new ReportDefinitionLoader().load(definitions);

        assertEquals(8, reports.size());
        assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7, 8),
            reports.stream().map(report -> report.report().reportId).collect(java.util.stream.Collectors.toSet()));
        assertTrue(reports.stream().allMatch(report -> !report.sql().isBlank()));
        assertTrue(reports.stream().noneMatch(report -> report.sql().contains("INSERT INTO reports")));
    }

    @Test
    void rejectsQueryParameterOutsideDeclaredList(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("query.sql"), "SELECT #{p1} AS n1");
        Files.writeString(directory.resolve("7_Invalid.report.yaml"), """
            id: 7
            name: InvalidReport
            type: summary
            displayName: Invalid
            query: query.sql
            parameters:
              - { name: organizationId, type: integer, required: true, displayName: Organization }
            fields:
              - { name: count, column: n1, type: integer, displayName: Count }
            """);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> new ReportDefinitionLoader().load(directory));

        assertTrue(error.getMessage().contains("references p1"));
        assertFalse(error.getMessage().contains("SELECT"));
    }

    @Test
    void rejectsLegacyReportGroupProperty(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("7_Legacy.report.yaml"), """
            id: 7
            name: LegacyReport
            type: summary
            displayName: Legacy
            query: query.sql
            fields:
              - { name: count, column: n1, type: integer, displayName: Count }
            reportGroups: [2]
            """);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> new ReportDefinitionLoader().load(directory));

        assertTrue(error.getMessage().contains("unknown report property: reportGroups"));
    }
}
