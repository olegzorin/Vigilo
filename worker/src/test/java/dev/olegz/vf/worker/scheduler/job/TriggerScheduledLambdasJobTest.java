package dev.olegz.vf.worker.scheduler.job;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Consumer;

import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.core.event.ScheduledEvent;
import dev.olegz.vf.core.service.lambda.LambdaClientService;
import dev.olegz.vf.messaging.ConfirmingMessageProducer;
import dev.olegz.vf.messaging.Topics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TriggerScheduledLambdasJobTest {
    @Test
    void publishesScheduledEventWithBrokerConfirmation() {
        ScheduledEvent event = new ScheduledEvent(21, 101, List.of("morning"));
        event.eventId = "event-1";
        event.time = 123_000L;
        LambdaClientService clientService = proxy(LambdaClientService.class, (proxy, method, args) -> {
            if (method.getName().equals("triggerScheduledLambdas")) {
                @SuppressWarnings("unchecked")
                Consumer<ScheduledEvent> consumer = (Consumer<ScheduledEvent>) args[0];
                consumer.accept(event);
            }
            return defaultValue(method.getReturnType());
        });
        RecordingProducer producer = new RecordingProducer();

        new TriggerScheduledLambdasJob(clientService, producer).run();

        ScheduledEvent published = BytesMapper.readValue(producer.payload, ScheduledEvent.class);
        assertEquals(Topics.LAMBDA_SCHEDULE, producer.topic);
        assertEquals(1, producer.confirmedSends);
        assertEquals(0, producer.asyncSends);
        assertEquals(event.eventId, published.eventId);
        assertEquals(event.scheduleIds, published.scheduleIds);
    }

    private static final class RecordingProducer implements ConfirmingMessageProducer {
        private int asyncSends;
        private int confirmedSends;
        private String topic;
        private byte[] payload;

        @Override public void send(String topic, byte[] value) { asyncSends++; }
        @Override public void sendAndAwait(String topic, byte[] value) {
            this.topic = topic;
            this.payload = value;
            confirmedSends++;
        }
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
