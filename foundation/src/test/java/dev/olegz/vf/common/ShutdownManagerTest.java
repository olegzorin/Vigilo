package dev.olegz.vf.common;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ShutdownManagerTest {
    @Test
    void executesHooksRegisteredDuringAndAfterShutdown() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp",
            System.getProperty("java.class.path"),
            ConcurrentRegistrationProcess.class.getName());
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes());

        assertEquals(0, process.waitFor(), output);
    }

    public static class ConcurrentRegistrationProcess {
        public static void main(String[] args) throws InterruptedException {
            AtomicInteger executions = new AtomicInteger();
            CountDownLatch firstHookStarted = new CountDownLatch(1);
            CountDownLatch releaseFirstHook = new CountDownLatch(1);

            ShutdownManager.register(() -> {
                executions.incrementAndGet();
                firstHookStarted.countDown();
                await(releaseFirstHook);
            });

            Thread shutdownThread = Thread.ofPlatform().start(ShutdownManager::shutdown);
            firstHookStarted.await();

            ShutdownManager.register(executions::incrementAndGet);
            releaseFirstHook.countDown();
            shutdownThread.join();

            ShutdownManager.register(executions::incrementAndGet);

            if (executions.get() != 3) {
                throw new AssertionError("Expected 3 hook executions, got " + executions.get());
            }
        }

        private static void await(CountDownLatch latch) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }
}
