package dev.olegz.vf.core.service.notification;

import java.util.Map;

/** Publishes notifications to named topics without exposing provider-specific topic identifiers. */
public interface NotificationService {

    void publish(String topicName, String subject, String message);

    void publish(String topicName, String subject, String message, Map<String, Integer> numericAttributes);
}
