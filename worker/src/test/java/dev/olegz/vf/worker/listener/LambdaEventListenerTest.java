package dev.olegz.vf.worker.listener;

import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.core.event.ResetEvent;
import dev.olegz.vf.core.event.ScheduledEvent;
import dev.olegz.vf.core.event.TriggerEvent;
import dev.olegz.vf.messaging.RetryableMessageException;
import dev.olegz.vf.worker.service.LambdaTriggeringService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LambdaEventListenerTest {
    @Test
    void triggerResetAndScheduleAreProcessedWithTypeScopedStableIds() {
        RecordingTriggeringService service = new RecordingTriggeringService();
        TriggerEvent trigger = new TriggerEvent(TriggerEvent.TRIGGER_LOCATION_EVENT, 21);
        trigger.eventId = "same-external-id";
        ResetEvent reset = new ResetEvent(21, 101);
        reset.eventId = "same-external-id";
        ScheduledEvent scheduled = new ScheduledEvent(21, 101, java.util.List.of("morning"));
        scheduled.eventId = "same-external-id";

        new LambdaInputListener(service).onMessage(BytesMapper.writeValue(trigger));
        String triggerId = service.eventId;
        assertEquals(trigger.locationId, service.triggerEvent.locationId);
        assertEquals(trigger.trigger, service.triggerEvent.trigger);

        new LambdaResetListener(service).onMessage(BytesMapper.writeValue(reset));
        assertEquals(64, triggerId.length());
        assertEquals(64, service.eventId.length());
        assertNotEquals(triggerId, service.eventId);
        assertEquals(reset.lambdaAssignmentId, service.resetEvent.lambdaAssignmentId);
        String resetId = service.eventId;

        new LambdaScheduleListener(service).onMessage(BytesMapper.writeValue(scheduled));
        assertNotEquals(triggerId, service.eventId);
        assertNotEquals(resetId, service.eventId);
        assertEquals(scheduled.scheduleIds, service.scheduledEvent.scheduleIds);
    }

    @Test
    void processingFailureRequestsKafkaRedelivery() {
        RecordingTriggeringService service = new RecordingTriggeringService();
        service.failure = new RuntimeException("database unavailable");

        RetryableMessageException thrown = assertThrows(RetryableMessageException.class,
            () -> new LambdaResetListener(service).onMessage(BytesMapper.writeValue(new ResetEvent(21, 101))));

        assertSame(service.failure, thrown.getCause());
    }

    @Test
    void malformedPayloadIsRejectedInsteadOfRetriedForever() {
        RecordingTriggeringService service = new RecordingTriggeringService();

        assertThrows(RuntimeException.class, () -> new LambdaInputListener(service).onMessage(new byte[]{1, 2, 3}));
        assertEquals(0, service.calls);
    }

    private static final class RecordingTriggeringService extends LambdaTriggeringService {
        private int calls;
        private String eventId;
        private TriggerEvent triggerEvent;
        private ResetEvent resetEvent;
        private ScheduledEvent scheduledEvent;
        private RuntimeException failure;

        private RecordingTriggeringService() {
            super(null, null, null, null, null);
        }

        @Override
        public void processDurable(String eventId, TriggerEvent event) {
            record(eventId);
            triggerEvent = event;
        }

        @Override
        public void processDurable(String eventId, ResetEvent event) {
            record(eventId);
            resetEvent = event;
        }

        @Override
        public void processDurable(String eventId, ScheduledEvent event) {
            record(eventId);
            scheduledEvent = event;
        }

        private void record(String eventId) {
            calls++;
            if (failure != null) throw failure;
            this.eventId = eventId;
        }
    }
}
