package dev.olegz.vf.worker.listener;

import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.core.dao.LambdaStatisticsDao;
import dev.olegz.vf.core.domain.lambdaoperation.LambdaOperationRequest;
import dev.olegz.vf.core.messaging.MessageDispatcher;
import dev.olegz.vf.messaging.MessageListener;
import dev.olegz.vf.worker.exception.RetryException;
import dev.olegz.vf.worker.service.LambdaDeploymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LambdaOperationListener implements MessageListener {
    private static final Logger logger = LoggerFactory.getLogger(LambdaOperationListener.class);

    private final LambdaDeploymentService lambdaDeploymentService;
    private final LambdaStatisticsDao lambdaStatisticsDao;

    public LambdaOperationListener(LambdaDeploymentService lambdaDeploymentService, LambdaStatisticsDao lambdaStatisticsDao)
    {
        this.lambdaDeploymentService = lambdaDeploymentService;
        this.lambdaStatisticsDao = lambdaStatisticsDao;
    }

    @Override
    public void onMessage(byte[] message) {
        LambdaOperationRequest request = null;
        try {
            request = BytesMapper.readValue(message, LambdaOperationRequest.class);
            logger.debug("Request: {}", request);

            switch (request.type) {
                case LambdaOperationRequest.TYPE_LAMBDA_CODE_UPLOAD -> lambdaDeploymentService.processCodeUpload(request.lambdaId, request.requestId);
                case LambdaOperationRequest.TYPE_DELETE_LAMBDA_ERRORS -> lambdaStatisticsDao.deleteLambdaErrors(request.lambdaVersionId());
                default -> logger.error("Unsupported lambda operation request type: " + request);
            }
        } catch (RetryException e) {
            if ((request != null) && request.retry()) MessageDispatcher.sendLambdaOperation(request);
        } catch (Exception e) {
            logger.error("Exception in processing lambda operation request " + request, e);
        }
    }

}
