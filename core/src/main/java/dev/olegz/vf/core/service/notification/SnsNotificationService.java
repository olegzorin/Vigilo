package dev.olegz.vf.core.service.notification;

import java.util.Map;

import dev.olegz.vf.aws.sns.SnsSupport;
import org.springframework.stereotype.Service;

/** Notification service backed by the real or local SNS client selected by the AWS module. */
@Service
public class SnsNotificationService implements NotificationService {

    @Override
    public void publish(String topicName, String subject, String message) {
        String topicArn = SnsSupport.makeSnsTopic(topicName);
        SnsSupport.publish(topicArn, subject, message);
    }

    @Override
    public void publish(String topicName, String subject, String message, Map<String, Integer> numericAttributes) {
        String topicArn = SnsSupport.makeSnsTopic(topicName);
        SnsSupport.publish(topicArn, subject, message, numericAttributes);
    }
}
