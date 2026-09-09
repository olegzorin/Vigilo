package dev.olegz.vf.core.domain.lambdabuild;

import java.util.UUID;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.props.DurationProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersion;

public class LambdaCodeUpload {
    private static final String AWS_RESOURCE_PREFIX = "dev-botlab-";

    public String getImageTag() {
        return codeObjectId.substring(codeObjectId.lastIndexOf('/') + 1);
    }

    /**
     * @return the base Lambda function name for the requested lane; the asynchronous lane uses the
     * {@code -async} suffix while the default lane remains unsuffixed
     */
    public String getBaseFunctionName(InvocationLane lane) {
        return AWS_RESOURCE_PREFIX + "lambda-%d-%d%s".formatted(
            lambdaId, lambdaVersionId, lane == InvocationLane.ASYNC ? "-async" : "");
    }

    public String getFunctionName(InvocationLane lane) {
        return lane == InvocationLane.ASYNC ? asyncFunctionName : functionName;
    }

    public void setFunctionName(InvocationLane lane, String functionName) {
        if (lane == InvocationLane.ASYNC) {
            this.asyncFunctionName = functionName;
        } else {
            this.functionName = functionName;
        }
    }

    public int uploadId;
    public int lambdaId;
    public int lambdaVersionId;
    public int userId;
    public byte arch;
    public int memory;
    public int timeout;
    public String functionName;
    public String asyncFunctionName;
    public String stageImage;
    public String baseImage;
    public String repoName;
    public String codeObjectId;
    public Datetime statusDate;
    public UploadStatus status;
    public String message;
    public Datetime functionDeletionDate;
    public String functionDeletionInfo;

    public Lambda lambda;
    public LambdaVersion lambdaVersion;

    private LambdaCodeUpload() {
    }

    public static LambdaCodeUpload create(BuildConfig buildConfig, int lambdaId, int lambdaVersionId, int userId) {
        LambdaCodeUpload upload = new LambdaCodeUpload();
        upload.arch = buildConfig.arch;
        upload.baseImage = buildConfig.baseImage;
        upload.stageImage = buildConfig.stageImage;
        upload.lambdaId = lambdaId;
        upload.repoName = AWS_RESOURCE_PREFIX + "lambda-%d".formatted(lambdaId);
        upload.codeObjectId = "code/%d/%s".formatted(lambdaId, UUID.randomUUID());
        upload.lambdaVersionId = lambdaVersionId;
        upload.userId = userId;
        upload.status = UploadStatus.CREATED;
        upload.statusDate = Datetime.now();

        return upload;
    }

    public boolean inProgress() {
        return switch (status) {
            case COMPLETED, FAILED -> false;
            case CREATED -> true;
            case IN_PROGRESS -> Datetime.now().getTime() < statusDate.getTime() +
                PropertyStore.getDuration(DurationProp.LAMBDA_DEPLOYMENT_TIMEOUT).toMillis();
        };
    }

    @Override
    public String toString() {
        return "{uploadId=" + uploadId +
            ", status=" + status +
            ", statusDate=" + statusDate +
            ", lambdaId=" + lambdaId +
            ", lambdaVersionId=" + lambdaVersionId +
            ", codeObjectId=" + codeObjectId +
            ", arch=" + arch +
            ", baseImage=" + baseImage +
            ", stageImage=" + stageImage +
            ", userId=" + userId +
            ", repoName=" + repoName +
            (lambda != null ? ", lambda=" + lambda : "") +
            (message != null ? ", message=" + message : "") +
            (functionName != null ? ", functionName=" + functionName : "") +
            (functionDeletionDate != null ? ", functionDeletionDate=" + functionDeletionDate : "") +
            '}';
    }

}
