package dev.olegz.vf.worker.listener;

import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.core.domain.lambdarun.LambdaError;
import dev.olegz.vf.core.service.lambda.LambdaWorkerService;
import dev.olegz.vf.messaging.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Consumes lambda run errors published by the worker's lambda execution flow (see
 * {@code MessageDispatcher.sendLambdaError}) from the {@code lambda-error} topic and delegates each to
 * {@link LambdaWorkerService#registerLambdaError}. Oversized error messages are truncated by the
 * producer to fit the broker limit, so every error arrives over the topic.
 */
@Component
public class LambdaErrorListener implements MessageListener {
    private static final Logger logger = LoggerFactory.getLogger(LambdaErrorListener.class);

    private final LambdaWorkerService lambdaWorkerService;

    public LambdaErrorListener(LambdaWorkerService lambdaWorkerService) {
        this.lambdaWorkerService = lambdaWorkerService;
    }

    @Override
    public void onMessage(byte[] messageBody) {
        try {
            LambdaError lambdaError = BytesMapper.readValue(messageBody, LambdaError.class);
            lambdaWorkerService.registerLambdaError(lambdaError);
        } catch (Exception e) {
            logger.error("Exception in processing lambda error", e);
        }
    }
}
