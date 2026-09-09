package dev.olegz.vf.worker.listener;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.core.event.TriggerEvent;
import dev.olegz.vf.messaging.MessageListener;
import dev.olegz.vf.messaging.RetryableMessageException;
import dev.olegz.vf.worker.service.LambdaTriggeringService;
import org.springframework.stereotype.Component;

@Component
public class LambdaInputListener implements MessageListener {
    private static final String EVENT_TYPE = "TRIGGER";
    private final LambdaTriggeringService lambdaTriggeringService;

    public LambdaInputListener(LambdaTriggeringService lambdaTriggeringService) {
        this.lambdaTriggeringService = lambdaTriggeringService;
    }

    @Override
    public void onMessage(byte[] message) {
        TriggerEvent event = BytesMapper.readValue(message, TriggerEvent.class);
        try {
            lambdaTriggeringService.processDurable(eventId(event.eventId, EVENT_TYPE, message), event);
        } catch (RuntimeException e) {
            throw new RetryableMessageException("Cannot process lambda trigger event", e);
        }
    }

    static String eventId(String suppliedId, String type, byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(type.getBytes(StandardCharsets.UTF_8));
            byte[] identity = (suppliedId == null) || suppliedId.isBlank()
                ? payload
                : suppliedId.getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(identity));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
