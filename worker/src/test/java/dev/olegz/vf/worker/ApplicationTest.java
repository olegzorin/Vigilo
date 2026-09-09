package dev.olegz.vf.worker;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.messaging.MessageBroker;
import dev.olegz.vf.messaging.MessagingProvider;
import dev.olegz.vf.messaging.ScopedMessageBroker;
import dev.olegz.vf.messaging.Topics;
import dev.olegz.vf.messaging.consumer.ConsumerConfig;
import dev.olegz.vf.messaging.consumer.ConsumerMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationTest {
    @BeforeAll
    static void oneTimeSetUp() {
        PropertyStore.start();
    }

    @Test
    void invocationRequestsUseCompletionBasedConcurrentConsumption() {
        Map<String, ConsumerConfig> registrations = new HashMap<>();
        ScopedMessageBroker kafka = broker(ScopedMessageBroker.class, (method, args) -> {
            if (method.equals("setMessageListeners")) {
                registrations.put((String) args[0], (ConsumerConfig) args[2]);
            }
        });

        application().registerStreamingListeners(kafka, broker(MessageBroker.class, (_, _) -> {}));

        ConsumerConfig config = registrations.get(Topics.LAMBDA_INVOKE_REQUEST);
        assertEquals(ConsumerMode.CONCURRENT, config.mode());
        assertEquals(10, config.poolSize());
        assertEquals(10, config.maxPollRecords());
    }

    @Test
    void listenerRegistrationFailurePropagates() {
        RuntimeException failure = new RuntimeException("Kafka unavailable");
        ScopedMessageBroker kafka = broker(ScopedMessageBroker.class, (method, args) -> {
            if (method.equals("setMessageListeners") && Topics.LAMBDA_INPUT.equals(args[0])) throw failure;
        });

        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
            application().registerStreamingListeners(kafka, broker(MessageBroker.class, (_, _) -> {})));

        assertSame(failure, thrown);
    }

    private static Application application() {
        return new Application(null, null, null, null, null, null, null, null, null);
    }

    private static <T> T broker(Class<T> type, Invocation invocation) {
        Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (_, method, args) -> {
            if (method.getName().equals("provider")) return MessagingProvider.KAFKA;
            if (method.getReturnType() == int.class) return 0;
            invocation.call(method.getName(), args);
            return null;
        });
        return type.cast(proxy);
    }

    @FunctionalInterface
    private interface Invocation {
        void call(String method, Object[] args);
    }
}
