package dev.olegz.vf.report;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import dev.olegz.vf.registry.config.DataSourceConfig;
import dev.olegz.vf.registry.config.RegistryConfig;
import dev.olegz.vf.report.rest.ReportRestConfig;
import dev.olegz.vf.report.storage.S3ReportOutputStore;

class ApplicationConfigurationTest {
    @Test
    void applicationImportsStandaloneReportConfiguration() {
        assertNotNull(Application.class.getAnnotation(SpringBootApplication.class));
        Import imports = Application.class.getAnnotation(Import.class);
        assertNotNull(imports);
        assertTrue(Arrays.asList(imports.value()).containsAll(
            Arrays.asList(RegistryConfig.class, DataSourceConfig.class, ReportConfig.class, ReportRestConfig.class)));

        Import reportImports = ReportConfig.class.getAnnotation(Import.class);
        assertNotNull(reportImports);
        assertTrue(Arrays.asList(reportImports.value()).contains(S3ReportOutputStore.class));
    }
}
