package dev.olegz.vf.api.lambda.deployment;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import dev.olegz.vf.aws.ecr.EcrPublicSupport;
import dev.olegz.vf.common.props.PropertyStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Preloads and refreshes the cached AWS Lambda Python image catalog. */
public final class PythonImageCatalogLifecycle {
    private static final Logger logger = LoggerFactory.getLogger(PythonImageCatalogLifecycle.class);

    private static final long REFRESH_INTERVAL = PropertyStore.getLong(
        "vf.aws.ecrPublic.catalogRefreshInterval",
        Duration.ofDays(1).toMillis()
    );

    private final Runnable catalogRefresh;
    private ScheduledExecutorService scheduler;

    public PythonImageCatalogLifecycle() {
        this(EcrPublicSupport::refreshPythonImageTags);
    }

    PythonImageCatalogLifecycle(Runnable catalogRefresh) {
        this.catalogRefresh = catalogRefresh;
    }

    @PostConstruct
    public void initialize() {
        scheduler = Executors.newSingleThreadScheduledExecutor(
            runnable -> Thread.ofPlatform()
                .daemon()
                .name("EcrPublicPythonCatalogRefresh")
                .unstarted(runnable)
        );
        scheduler.scheduleAtFixedRate(
            this::refreshSafely,
            0,
            REFRESH_INTERVAL,
            TimeUnit.MILLISECONDS
        );
    }

    private void refreshSafely() {
        try {
            catalogRefresh.run();
        } catch (Exception e) {
            logger.error(
                "Exception while refreshing ECR Public Python image catalog; retaining previous catalog",
                e
            );
        }
    }

    @PreDestroy
    public void shutdown() {
        if (scheduler != null) scheduler.shutdownNow();
    }
}
