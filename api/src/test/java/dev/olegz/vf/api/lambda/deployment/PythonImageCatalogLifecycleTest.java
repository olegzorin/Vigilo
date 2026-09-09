package dev.olegz.vf.api.lambda.deployment;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonImageCatalogLifecycleTest {

    @Test
    void initializationDoesNotWaitForInitialRefresh() throws InterruptedException {
        CountDownLatch refreshStarted = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        PythonImageCatalogLifecycle lifecycle = new PythonImageCatalogLifecycle(() -> {
            refreshStarted.countDown();
            try {
                releaseRefresh.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        try {
            assertTimeoutPreemptively(Duration.ofSeconds(5), lifecycle::initialize);
            assertTrue(refreshStarted.await(1, TimeUnit.SECONDS));
        } finally {
            releaseRefresh.countDown();
            lifecycle.shutdown();
        }
    }
}
