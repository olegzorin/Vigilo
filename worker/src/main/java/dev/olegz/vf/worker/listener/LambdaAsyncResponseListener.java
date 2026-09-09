package dev.olegz.vf.worker.listener;

import java.nio.charset.StandardCharsets;

import dev.olegz.vf.common.objectmap.StringMapper;
import dev.olegz.vf.core.domain.lambdarun.input.LambdaInput;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import dev.olegz.vf.messaging.MessageListener;
import dev.olegz.vf.worker.domain.LambdaInvokeRequest;
import dev.olegz.vf.worker.domain.LambdaOutput;
import dev.olegz.vf.worker.service.LambdaFunctionDeployer;
import dev.olegz.vf.worker.service.LambdaRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import static java.lang.Boolean.TRUE;

/**
 * Listens for asynchronous Lambda invocation results from the SQS queue configured by
 * {@link LambdaFunctionDeployer}. The deployer creates the queue named by
 * {@link Lambda#SQS_RESULT_QUEUE} and writes it to the Lambda function event
 * invoke {@code Destinations} config for both success and failure results of the asynchronous function
 * version.
 */
@Component
public class LambdaAsyncResponseListener implements MessageListener {
    private static final Logger logger = LoggerFactory.getLogger(LambdaAsyncResponseListener.class);

    private final LambdaRunService lambdaRunService;

    public LambdaAsyncResponseListener(LambdaRunService lambdaRunService) {
        this.lambdaRunService = lambdaRunService;
    }

    @Override
    public void onMessage(byte[] message) {
        String body = new String(message, StandardCharsets.UTF_8);
        logger.debug("onMessage() " + body);

        final AsyncResponse asyncResponse;
        try {
            asyncResponse = StringMapper.readValue(body, AsyncResponse.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse message from asynchronous Lambda destination", e);
        }

        if ((asyncResponse.requestContext == null) || (asyncResponse.requestContext.functionArn == null) ||
            (asyncResponse.requestPayload == null) || (asyncResponse.requestPayload.runId == 0L) ||
            (asyncResponse.responseContext == null))
        {
            throw new IllegalArgumentException("Missing data in message from asynchronous Lambda destination");
        }

        String[] functionArn = asyncResponse.requestContext.functionArn.split(":", 7);
        if (functionArn.length != 7) {
            throw new IllegalArgumentException("Invalid function ARN in asynchronous Lambda destination");
        }

        // Reconstruct invoke request from the request payload
        LambdaInvokeRequest lambdaInvokeRequest = new LambdaInvokeRequest();
        lambdaInvokeRequest.lambdaFunction = functionArn[6];
        lambdaInvokeRequest.logging = TRUE.equals(asyncResponse.requestPayload.logEvents);
        lambdaInvokeRequest.awsRequestId = asyncResponse.requestContext.requestId;
        lambdaInvokeRequest.requestId = asyncResponse.requestPayload.runId;
        lambdaInvokeRequest.lambdaAssignmentId = asyncResponse.requestPayload.id;
        lambdaInvokeRequest.lambdaVersionId = asyncResponse.requestPayload.lambdaVersionId;
        lambdaInvokeRequest.lambdaId = asyncResponse.requestPayload.lambdaId;
        lambdaInvokeRequest.lane = asyncResponse.requestPayload.lane;

        String serviceError = asyncResponse.deliveryError != null ? "Delivery error: " + asyncResponse.deliveryError :
            asyncResponse.responseContext.functionError != null ? "Execution error: " + asyncResponse.responseContext :
                null;

        LambdaOutput lambdaOutput = asyncResponse.responsePayload;
        if (lambdaOutput == null) {
            lambdaOutput = new LambdaOutput();
            if (serviceError != null) {
                lambdaOutput.errorMessage = serviceError;
            } else {
                logger.error("Empty lambda output:\n" + body);
            }
        } else if ((lambdaOutput.errorMessage == null) && (serviceError != null)) {
            lambdaOutput.errorMessage = serviceError;
        }

        lambdaRunService.processAsyncResult(lambdaInvokeRequest, lambdaOutput);
    }

    /**
     * This class represents a message that Lambda automatically sends to the destination
     * specified in the Lambda function configuration for asynchronous invocations. We use the
     * SQS queue from {@link Lambda#SQS_RESULT_QUEUE} as the destination for both
     * success and failure results (see {@link LambdaFunctionDeployer}).
     * Destinations allow the server to have full control over asynchronous executions
     * without the need for lambda developers to manually code sending the lambda output to SQS.
     * See:
     * https://aws.amazon.com/blogs/compute/introducing-aws-lambda-destinations/
     */
    private static class AsyncResponse {
        public String version;                  // "1.0"
        public String timestamp;                // "2019-11-24T23:08:25.651Z"
        public RequestContext requestContext;
        public LambdaInput requestPayload;
        public ResponseContext responseContext;
        public LambdaOutput responsePayload;
        public DeliveryError deliveryError;

        @Override
        public String toString() {
            return "{version=" + version + ", timestamp=" + timestamp + ", requestContext=" + requestContext +
                ", requestPayload=" + requestPayload + ", responseContext=" + responseContext +
                (responsePayload == null ? "" : ", responsePayload=" + responsePayload) +
                '}';
        }
    }

    private static class RequestContext {
        public String requestId;            // AWS RequestId
        public String functionArn;
        public String condition;            // "Success", "RetriesExhausted"
        public int approximateInvokeCount;

        @Override
        public String toString() {
            return "{requestId=" + requestId + ", functionArn=" + functionArn + ", condition=" + condition +
                ", approximateInvokeCount=" + approximateInvokeCount + '}';
        }
    }

    private static class ResponseContext {
        public int statusCode;              // 200
        public String functionError;        // "Unhandled", "Handled"
        public String executedVersion;      // "$LATEST"

        @Override
        public String toString() {
            return "{statusCode=" + statusCode + ", functionError=" + functionError + ", executedVersion=" + executedVersion + '}';
        }
    }

    private static class DeliveryError {
        public int statusCode;          // 413
        public String errorCode;        // "RequestEntityTooLargeException"
        public String errorMessage;     // "The total destination payload is too large. RequestPayload size: 6958 bytes, ResponsePayload size: 593762 bytes"

        @Override
        public String toString() {
            return "{statusCode=" + statusCode + ", errorCode=" + errorCode + ", errorMessage=" + errorMessage + '}';
        }
    }

}
