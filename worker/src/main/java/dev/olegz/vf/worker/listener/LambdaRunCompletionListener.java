package dev.olegz.vf.worker.listener;

import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.core.domain.lambdarun.LambdaRunContext;
import dev.olegz.vf.messaging.MessageListener;
import dev.olegz.vf.messaging.RetryableMessageException;
import dev.olegz.vf.worker.service.LambdaRunService;
import org.springframework.stereotype.Component;

@Component
public class LambdaRunCompletionListener implements MessageListener {
    private final LambdaRunService lambdaRunService;

    public LambdaRunCompletionListener(LambdaRunService lambdaRunService) {
        this.lambdaRunService = lambdaRunService;
    }

    @Override
    public void onMessage(byte[] messageBytes) {
        try {
            LambdaRunContext context = BytesMapper.readValue(messageBytes, LambdaRunContext.class);
            lambdaRunService.processRunCompletion(context);
        } catch (Exception e) {
            throw new RetryableMessageException("Cannot process lambda run completion", e);
        }
    }
}
