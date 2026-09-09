package dev.olegz.vf.api;

import dev.olegz.vf.api.lambda.deployment.PythonImageCatalogLifecycle;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.config.CoreConfig;
import dev.olegz.vf.registry.config.DataSourceConfig;
import dev.olegz.vf.core.system.ResourceManager;
import dev.olegz.vf.report.ReportConfig;
import dev.olegz.vf.report.rest.ReportRestConfig;
import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.servlet.context.ServletComponentScan;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@ServletComponentScan()
@Import({CoreConfig.class, DataSourceConfig.class, ReportConfig.class, ReportRestConfig.class})
public class Application implements ApplicationListener<ApplicationReadyEvent> {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private static final ResourceManager.ResourceId[] RESOURCES = {
        ResourceManager.ResourceId.STREAM_PRODUCER,
        ResourceManager.ResourceId.RUNTIME
    };

    private static volatile boolean stopped;

    public static boolean isStopped() {
        return stopped;
    }

    public static void main(String[] args) {
        PropertyStore.start();
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public PythonImageCatalogLifecycle pythonImageCatalogLifecycle() {
        return new PythonImageCatalogLifecycle();
    }

    @Override
    public void onApplicationEvent(@NonNull ApplicationReadyEvent event) {
        logger.warn("Application starting");

        try {
            ResourceManager.open(RESOURCES);
        } catch (Exception e) {
            logger.error("Exception in application starting", e);
        }

        logger.warn("Application started");
    }

    @PreDestroy
    public void destroy() {
        stopped = true;
        logger.warn("Application stopping");

        try {
            ResourceManager.close(RESOURCES);
        } catch (Exception e) {
            logger.error("Exception in closing resources", e);
        }

        logger.warn("Application stopped");
    }
}
