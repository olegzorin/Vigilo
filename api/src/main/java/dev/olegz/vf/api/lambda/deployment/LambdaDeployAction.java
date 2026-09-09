package dev.olegz.vf.api.lambda.deployment;

import java.util.HashMap;
import java.util.List;

import dev.olegz.vf.api.web.support.ActionContext;
import dev.olegz.vf.api.web.support.ActionResponse;
import dev.olegz.vf.aws.ecr.EcrPublicSupport;
import dev.olegz.vf.aws.s3.S3Support;
import dev.olegz.vf.common.exception.MissingParameterException;
import dev.olegz.vf.common.exception.WrongParameterValueException;
import dev.olegz.vf.core.domain.lambdabuild.LambdaCodeUpload;
import dev.olegz.vf.core.domain.lambdabuild.BuildConfig;
import dev.olegz.vf.core.domain.lambdabuild.UploadStatus;
import dev.olegz.vf.core.domain.lambdaoperation.LambdaOperationRequest;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import dev.olegz.vf.core.messaging.MessageDispatcher;
import dev.olegz.vf.core.service.lambda.LambdaManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LambdaDeployAction {
    private static final Logger logger = LoggerFactory.getLogger(LambdaDeployAction.class);

    private final LambdaManagementService lambdaManagementService;

    public LambdaDeployAction(LambdaManagementService lambdaManagementService) {
        this.lambdaManagementService = lambdaManagementService;
    }

    /**
     * Uploads a lambda code archive and starts its asynchronous container-image build.
     * <p>
     * {@code buildTarget} combines the Python version and processor architecture in
     * {@code <python-version>-<architecture>} form, for example {@code 3.12-arm64}
     * or {@code 3.11-x86_64}. It selects the Python images used to build and run the
     * lambda and the processor architecture of the deployed Lambda function.
     *
     * @param ctx current request context, including the uploading user
     * @param lambdaId lambda whose code is being uploaded
     * @param buildTarget supported Python version and processor architecture combination
     * @param contentType media type of the uploaded archive
     * @param content lambda code archive
     * @return response containing the asynchronous upload request ID
     */
    public Response postLambdaCode(ActionContext ctx, int lambdaId, String buildTarget, String contentType, byte[] content) {
        if (logger.isDebugEnabled()) {
            logger.debug(">postLambdaCode() lambdaId=" + lambdaId + ", buildTarget=" + buildTarget + ", contentType=" + contentType +
                (content == null ? "" : ", content.length=" + content.length));
        }

        validateContent(content, contentType);

        // read deployment archive
        HashMap<String, byte[]> codeFiles = LambdaCodeArchive.extract(content, contentType);
        if (codeFiles.isEmpty()) {
            throw new WrongParameterValueException("No code files in the archive. Code file names must end with .py, .pyc, .json, .js");
        }

        BuildConfig buildConfig = BuildConfig.create(buildTarget, codeFiles);
        LambdaCodeUpload upload = lambdaManagementService.uploadLambdaCode(ctx.userId(), lambdaId, buildConfig);

        byte[] tarContent = LambdaCodeArchive.tar(codeFiles);
        String tarContentType = "application/x-tar";
        S3Support.createObject(Lambda.S3_BUCKET, upload.codeObjectId, tarContent, tarContentType, true);

        try {
            MessageDispatcher.sendLambdaOperation(LambdaOperationRequest.lambdaCodeUpload(upload.lambdaId, upload.uploadId));
        } catch (RuntimeException e) {
            String message = "Failed to dispatch upload request: " + e.getMessage();
            logger.error("{} uploadId={}", message, upload.uploadId, e);
            lambdaManagementService.failLambdaCodeUpload(upload, message);
        }

        Response response = new Response();
        response.requestId = upload.uploadId;

        logger.debug("<postLambdaCode() lambdaName={}", lambdaId);
        return response;
    }

    public Response getLambdaCodeUpload(ActionContext ctx, int lambdaId, int uploadId) {
        if (logger.isDebugEnabled()) {
            logger.debug(">getLambdaCodeUpload() lambdaId=" + lambdaId + ", uploadId=" + uploadId);
        }

        Response response = new Response();
        LambdaCodeUpload upload = lambdaManagementService.getLambdaCodeUpload(ctx.userId(), lambdaId, uploadId);
        if (upload != null) {
            response.result = new ApiUpload(upload);
        }

        logger.debug("<getLambdaCodeUpload()");
        return response;
    }

    public PythonImageTagsResponse getPythonImageTags() {
        return new PythonImageTagsResponse(EcrPublicSupport.getPythonImageTags());
    }

    public static class Response extends ActionResponse {
        public Integer requestId;
        public ApiUpload result;
    }

    public static class PythonImageTagsResponse extends ActionResponse {
        public final List<String> result;

        private PythonImageTagsResponse(List<String> result) {
            this.result = result;
            collectionTotalSize = result.size();
        }
    }

    public static class ApiUpload {
        public final UploadStatus status;
        public final String message;

        private ApiUpload(LambdaCodeUpload upload) {
            status = upload.status;
            message = upload.message;
        }
    }

    public static void validateContent(byte[] content, String contentType) {
        if (content == null || content.length == 0) {
            throw new MissingParameterException("Missing content");
        }

        if (contentType == null || contentType.isBlank()) {
            throw new MissingParameterException("Missing contentType");
        }
    }
}
