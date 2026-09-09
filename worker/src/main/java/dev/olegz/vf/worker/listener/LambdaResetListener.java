package dev.olegz.vf.worker.listener;

import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.core.event.ResetEvent;
import dev.olegz.vf.messaging.MessageListener;
import dev.olegz.vf.messaging.RetryableMessageException;
import dev.olegz.vf.worker.service.LambdaTriggeringService;
import org.springframework.stereotype.Component;

@Component
public class LambdaResetListener implements MessageListener {
    private static final String EVENT_TYPE = "RESET";
    private final LambdaTriggeringService lambdaTriggeringService;

    public LambdaResetListener(LambdaTriggeringService lambdaTriggeringService) {
        this.lambdaTriggeringService = lambdaTriggeringService;
    }

    @Override
    public void onMessage(byte[] message) {
        ResetEvent event = BytesMapper.readValue(message, ResetEvent.class);
        try {
            lambdaTriggeringService.processDurable(
                LambdaInputListener.eventId(event.eventId, EVENT_TYPE, message), event);
        } catch (RuntimeException e) {
            throw new RetryableMessageException("Cannot process lambda reset event", e);
        }
    }
}
