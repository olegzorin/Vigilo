package dev.olegz.vf.report;

import dev.olegz.vf.aws.client.AwsClients;
import dev.olegz.vf.aws.s3.S3Support;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.registry.config.DataSourceConfig;
import dev.olegz.vf.registry.config.RegistryConfig;
import dev.olegz.vf.report.rest.ReportRestConfig;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** Standalone entry point for the report REST service. */
@SpringBootApplication(scanBasePackages = "dev.olegz.vf.report.rest")
@Import({RegistryConfig.class, DataSourceConfig.class, ReportConfig.class, ReportRestConfig.class})
public class Application {
    public static void main(String[] args) {
        PropertyStore.start();
        SpringApplication.run(Application.class, args);
    }

    /** The API application closes these through CoreConfig; the standalone report app owns them here. */
    @PreDestroy
    public void shutdownAwsClients() {
        S3Support.shutdown();
        AwsClients.closeHttpClient();
    }
}
