package dev.olegz.vf.core.domain.lambdaoperation;

import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.messaging.Topics;

public class LambdaOperationRequest {
    public static final byte TYPE_LAMBDA_CODE_UPLOAD = 4;
    public static final byte TYPE_DELETE_LAMBDA_ERRORS = 15;
    public static final byte TYPE_DELETE_LAMBDA_ASSIGNMENTS = 18;

    private static final int MAX_ATTEMPT = PropertyStore.getInt("vf.async.maxAttempt", 10);

    public byte type;
    private int attempt;
    public Integer requestId;
    public Integer lambdaId;
    private Integer lambdaVersionId;

    @Override
    public String toString() {
        return "{type=" + type +
            (attempt > 0 ? ", attempt=" + attempt : "") +
            (requestId != null ? ", requestId=" + requestId : "") +
            (lambdaId != null ? ", lambdaId=" + lambdaId : "") +
            (lambdaVersionId != null ? ", lambdaVersionId=" + lambdaVersionId : "") +
            '}';
    }

    public LambdaOperationRequest() {
    }

    public static LambdaOperationRequest lambdaCodeUpload(int lambdaId, int uploadId) {
        LambdaOperationRequest request = new LambdaOperationRequest();
        request.type = TYPE_LAMBDA_CODE_UPLOAD;
        request.lambdaId = lambdaId;
        request.requestId = uploadId;
        return request;
    }

    public static LambdaOperationRequest deleteLambdaErrors(int lambdaVersionId) {
        LambdaOperationRequest request = new LambdaOperationRequest();
        request.type = TYPE_DELETE_LAMBDA_ERRORS;
        request.lambdaVersionId = lambdaVersionId;
        return request;
    }

    public static LambdaOperationRequest deleteLambdaAssignments(int lambdaId) {
        LambdaOperationRequest request = new LambdaOperationRequest();
        request.type = TYPE_DELETE_LAMBDA_ASSIGNMENTS;
        request.lambdaId = lambdaId;
        return request;
    }

    public Integer lambdaVersionId() {
        return lambdaVersionId;
    }

    public boolean retry() {
        attempt++;
        return attempt < MAX_ATTEMPT;
    }

    public String topic() {
        return type == TYPE_LAMBDA_CODE_UPLOAD ? Topics.LAMBDA_CODE_UPLOAD : Topics.OPERATIONS;
    }
}
