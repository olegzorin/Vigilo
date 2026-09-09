package dev.olegz.vf.core.messaging;

import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.core.domain.lambdaoperation.LambdaOperationRequest;
import dev.olegz.vf.core.domain.lambdarun.LambdaAssignmentLog;
import dev.olegz.vf.core.domain.lambdarun.LambdaError;
import dev.olegz.vf.messaging.ConfirmingMessageProducer;
import dev.olegz.vf.messaging.Messaging;
import dev.olegz.vf.messaging.MessagingProvider;
import dev.olegz.vf.messaging.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(MessageDispatcher.class);
    private static final ConfirmingMessageProducer producer = Messaging.producer(
        MessagingProvider.KAFKA, "misc", ConfirmingMessageProducer.class);

    public static void sendLambdaOperation(LambdaOperationRequest request) {
        String topic = request.topic();
        try {
            producer.sendAndAwait(topic, BytesMapper.writeValue(request));
        } catch (RuntimeException e) {
            logger.error("Exception in sending message to " + topic, e);
            throw e;
        }
    }

    /** Appended to a lambda error message whose text was truncated to fit the broker message-size limit. */
    private static final String TRUNCATION_MARKER = "…[truncated]";

    public static void sendLambdaError(LambdaError lambdaError) {
        try {
            int maxSize = Messaging.broker(MessagingProvider.KAFKA).maxMessageSize();
            byte[] bytes = BytesMapper.writeValue(lambdaError);
            if (bytes.length >= maxSize) {
                logger.info("Truncating large lambda error {}, serialized size {} exceeds broker limit {}",
                    lambdaError, bytes.length, maxSize);
                bytes = truncateToFit(lambdaError, maxSize);
            }
            producer.send(Topics.LAMBDA_ERROR, bytes);
        } catch (Exception e) {
            logger.error("Exception in sending lambda error", e);
        }
    }

    /**
     * Shrinks the error {@code message} (which for asynchronous runs already embeds the CloudWatch log
     * pointers) until the serialized {@link LambdaError} fits within {@code maxSize}, appending a
     * marker so the truncation is visible. Returns the serialized bytes to send.
     */
    private static byte[] truncateToFit(LambdaError lambdaError, int maxSize) {
        byte[] bytes = BytesMapper.writeValue(lambdaError);
        while ((bytes.length >= maxSize) && (lambdaError.message != null) && !lambdaError.message.isEmpty()) {
            int overflow = bytes.length - maxSize;
            // Cut the overflow plus headroom for the marker and multi-byte/JSON-escaping slack.
            int newLen = Math.max(0, lambdaError.message.length() - overflow - TRUNCATION_MARKER.length() - 64);
            lambdaError.message = lambdaError.message.substring(0, newLen) + TRUNCATION_MARKER;
            bytes = BytesMapper.writeValue(lambdaError);
        }
        return bytes;
    }

    public static void sendLambdaAssignmentLog(LambdaAssignmentLog lambdaAssignmentLog) {
        try {
            producer.send(Topics.LAMBDA_ASSIGNMENT_LOG, lambdaAssignmentLog);
        } catch (Exception e) {
            logger.error("Exception in sending lambda assignment log", e);
        }
    }

}
