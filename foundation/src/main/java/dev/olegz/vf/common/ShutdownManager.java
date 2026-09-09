package dev.olegz.vf.common;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;

import dev.olegz.vf.common.props.PropertyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates shutdown of shared application resources.
 */
public final class ShutdownManager {
    private static final Logger logger = LoggerFactory.getLogger(ShutdownManager.class);
    private static final Object lock = new Object();
    private static final Queue<Runnable> hooks = new ArrayDeque<>();

    private static boolean shuttingDown;
    private static boolean shutDown;

    private ShutdownManager() {
    }

    /**
     * Register a shutdown hook. Hooks registered while shutdown is in progress are added to the
     * current shutdown cycle; hooks registered after it finishes run immediately.
     */
    public static void register(Runnable hook) {
        Objects.requireNonNull(hook);

        synchronized (lock) {
            if (!shutDown) {
                hooks.add(hook);
                return;
            }
        }

        runHook(hook);
    }

    /**
     * Stop the property store and drain all registered hooks.
     */
    public static void shutdown() {
        synchronized (lock) {
            if (shuttingDown || shutDown) return;
            shuttingDown = true;
        }

        PropertyStore.shutdown();

        while (true) {
            Runnable hook;
            synchronized (lock) {
                hook = hooks.poll();
                if (hook == null) {
                    shutDown = true;
                    shuttingDown = false;
                    return;
                }
            }

            runHook(hook);
        }
    }

    private static void runHook(Runnable hook) {
        try {
            hook.run();
        } catch (Exception e) {
            logger.warn("Exception in shutdown hook", e);
        }
    }
}
