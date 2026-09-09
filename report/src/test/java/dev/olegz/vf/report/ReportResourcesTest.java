package dev.olegz.vf.report;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.junit.jupiter.api.Test;

class ReportResourcesTest {
    @Test
    void mapperIsValidAndSurveyReportsAreExcluded() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        try (InputStream mapper = loader.getResourceAsStream("vf/sqlmaps/ReportMapper.xml")) {
            assertNotNull(mapper);
            var configuration = new org.apache.ibatis.session.Configuration();
            new XMLMapperBuilder(mapper, configuration, "vf/sqlmaps/ReportMapper.xml", configuration.getSqlFragments()).parse();
            assertTrue(configuration.hasStatement("dev.olegz.vf.report.dao.mapper.ReportsMapper.selectReportForExecution"));
            assertTrue(configuration.hasStatement("dev.olegz.vf.report.dao.mapper.ReportsMapper.selectReportScheduleForUpdate"));
            assertFalse(configuration.hasStatement("dev.olegz.vf.report.dao.mapper.ReportsMapper.selectReportCollections"));
        }

        assertNull(loader.getResource("database/reports/sql/5_InappUsersFeedbackReport.sql"));
        assertNull(loader.getResource("database/reports/sql/32_SurveyResultsReport.sql"));
        assertNull(loader.getResource("database/reports/manual/voice_surveys.sql"));
        assertNull(loader.getResource("dev/olegz/vf/report/domain/CollectionReport.class"));
        assertNull(loader.getResource("dev/olegz/vf/report/domain/ReportCollection.class"));
        assertNull(loader.getResource("database/reports/collections/NurseShiftReportCollection.sql"));
        assertNull(loader.getResource("database/reports/migrations/20260212_725_report_collections.sql"));
        assertNull(loader.getResource("database/reports/migrations/20260308_migrate_report_schedules.sql"));
        assertNull(loader.getResource("dev/olegz/vf/report/service/SharedReports.class"));
        Path definitions = ReportTestPaths.definitions();
        assertTrue(Files.isRegularFile(definitions.resolve("1_NewUsers.report.yaml")));
        assertTrue(Files.isRegularFile(definitions.resolve("queries/1_NewUsers.sql")));

        assertNotNull(loader.getResource("dev/olegz/vf/report/storage/S3ReportOutputStore.class"));
    }

}
