package dev.olegz.vf.worker.listener;

import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.core.event.ScheduledEvent;
import dev.olegz.vf.messaging.MessageListener;
import dev.olegz.vf.messaging.RetryableMessageException;
import dev.olegz.vf.worker.service.LambdaTriggeringService;
import org.springframework.stereotype.Component;

@Component
public class LambdaScheduleListener implements MessageListener {
    private static final String EVENT_TYPE = "SCHEDULE";
    private final LambdaTriggeringService lambdaTriggeringService;

    public LambdaScheduleListener(LambdaTriggeringService lambdaTriggeringService) {
        this.lambdaTriggeringService = lambdaTriggeringService;
    }

    @Override
    public void onMessage(byte[] message) {
        ScheduledEvent event = BytesMapper.readValue(message, ScheduledEvent.class);
        try {
            lambdaTriggeringService.processDurable(
                LambdaInputListener.eventId(event.eventId, EVENT_TYPE, message), event);
        } catch (RuntimeException e) {
            throw new RetryableMessageException("Cannot process scheduled lambda event", e);
        }
    }
}
