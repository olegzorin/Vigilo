package dev.olegz.vf.core.system;

import java.util.concurrent.atomic.AtomicInteger;

import dev.olegz.vf.common.ShutdownManager;
import dev.olegz.vf.common.StartupSettingsValidator;
import dev.olegz.vf.common.VigiloEnvironment;
import dev.olegz.vf.messaging.Messaging;
import dev.olegz.vf.messaging.MessagingProvider;

public class ResourceManager {
    public enum ResourceId {
        /** Validate runtime settings and stop shared resources */
        RUNTIME,
        /** Close stream producers */
        STREAM_PRODUCER
    }

    private static final Object countsLock = new Object();
    private static final AtomicInteger[] counts = new AtomicInteger[ResourceId.values().length];
    static {
        for (int i = 0; i < counts.length; i++) {
            counts[i] = new AtomicInteger();
        }
    }

    public static void open(ResourceId[] resources) {
        for (ResourceId id : resources) {
            openResource(id);
        }
    }

    private static void openResource(ResourceId id) {
        final int ind = id.ordinal();

        if (counts[ind].get() == 0) {
            synchronized (countsLock) {
                if (counts[ind].get() == 0) {
                    switch (id) {
                        case RUNTIME -> {
                            VigiloEnvironment.requirePropertyFilesDir();
                            StartupSettingsValidator.validate();
                        }
                    }
                }

                counts[ind].incrementAndGet();
            }
        } else {
            counts[ind].incrementAndGet();
        }
    }

    public static void close(ResourceId[] resources) {
        // in back order
        for (int i = resources.length - 1; i >= 0; i--) close(resources[i]);
    }

    private static void close(ResourceId id) {
        int count = counts[id.ordinal()].decrementAndGet();

        if (count == 0) {
            switch (id) {
                case RUNTIME -> ShutdownManager.shutdown();
                case STREAM_PRODUCER -> Messaging.broker(MessagingProvider.KAFKA).closeProducers();
            }
        }
    }
}
