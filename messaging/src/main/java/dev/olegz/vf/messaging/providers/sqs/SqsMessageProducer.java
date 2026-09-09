package dev.olegz.vf.messaging.providers.sqs;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import dev.olegz.vf.aws.sqs.SqsSupport;
import dev.olegz.vf.messaging.MessageProducer;
import dev.olegz.vf.messaging.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SqsMessageProducer implements MessageProducer {
    private static final Logger logger = LoggerFactory.getLogger(SqsMessageProducer.class);

    private final String clientId;
    private final int maxMessageSize;
    private final int warnMessageSize;
    private final int messageRetentionSeconds;
    private final int visibilityTimeoutSeconds;

    SqsMessageProducer(String clientId, int maxMessageSize, int warnMessageSize,
        int messageRetentionSeconds, int visibilityTimeoutSeconds)
    {
        this.clientId = clientId;
        this.maxMessageSize = maxMessageSize;
        this.warnMessageSize = warnMessageSize;
        this.messageRetentionSeconds = messageRetentionSeconds;
        this.visibilityTimeoutSeconds = visibilityTimeoutSeconds;
    }

    @Override
    public void send(String topic, byte[] value) {
        String body = Base64.getEncoder().encodeToString(value);
        checkSize(topic, body);
        SqsSupport.sendMessage(topic, body, 0, messageRetentionSeconds, visibilityTimeoutSeconds);
    }

    void close() {
    }

    @Override
    public String toString() {
        return clientId;
    }

    private void checkSize(String topic, String body) {
        int size = body.getBytes(StandardCharsets.UTF_8).length;
        if (size > maxMessageSize) {
            throw new MessagingException("Message exceeds max size: size=" + size + ", maxSize=" + maxMessageSize
                + ", topic=" + topic + ", clientId=" + clientId);
        }
        if (size > warnMessageSize) {
            logger.warn("Large SQS message: size={}, warnSize={}, topic={}, clientId={}",
                size, warnMessageSize, topic, clientId);
        }
    }
}
