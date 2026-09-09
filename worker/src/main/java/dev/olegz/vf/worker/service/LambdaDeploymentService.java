package dev.olegz.vf.worker.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import dev.olegz.vf.aws.ecr.EcrSupport;
import dev.olegz.vf.aws.s3.S3Support;
import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.exception.ExternalException;
import dev.olegz.vf.common.io.ProcessRunner;
import dev.olegz.vf.common.props.DurationProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.common.util.CollectionOps;
import dev.olegz.vf.core.dao.LambdaCodeUploadDao;
import dev.olegz.vf.core.domain.lambdabuild.LambdaCodeUpload;
import dev.olegz.vf.core.domain.lambdabuild.BuildConfig;
import dev.olegz.vf.core.domain.lambdabuild.UploadStatus;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import dev.olegz.vf.core.service.lambda.LambdaLogService;
import dev.olegz.vf.core.service.lambda.LambdaWorkerService;
import dev.olegz.vf.core.service.notification.NotificationService;
import dev.olegz.vf.worker.LambdaAwsResources;
import dev.olegz.vf.worker.domain.DeleteFunctionParams;
import dev.olegz.vf.worker.domain.UpdateFunctionParams;
import dev.olegz.vf.worker.exception.BuildException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * @author Oleg Zorin
 */
@Service("lambdaDeploymentService")
public class LambdaDeploymentService {
    private static final Logger logger = LoggerFactory.getLogger(LambdaDeploymentService.class);
    private static final String ATTRIBUTE_DEV_TEAM_ID = "devTeamId";
    private static final String ATTRIBUTE_LAMBDA_ID = "lambdaId";
    private static final int EXPIRY_BATCH_SIZE = 100;

    private final LambdaCodeUploadDao lambdaCodeUploadDao;
    private final LambdaLogService lambdaLogService;
    private final LambdaWorkerService lambdaWorkerService;
    private final LambdaFunctionDeployer lambdaFunctionDeployer;
    private final NotificationService notificationService;

    public LambdaDeploymentService(LambdaCodeUploadDao lambdaCodeUploadDao, LambdaLogService lambdaLogService,
                                LambdaWorkerService lambdaWorkerService,
                                LambdaFunctionDeployer lambdaFunctionDeployer,
                                NotificationService notificationService) {
        this.lambdaCodeUploadDao = lambdaCodeUploadDao;
        this.lambdaLogService = lambdaLogService;
        this.lambdaWorkerService = lambdaWorkerService;
        this.lambdaFunctionDeployer = lambdaFunctionDeployer;
        this.notificationService = notificationService;
    }

    public void processCodeUpload(int lambdaId, int uploadId) {
        LambdaCodeUpload upload = lambdaWorkerService.getUploadForProcessing(lambdaId, uploadId);
        if (upload == null) {
            logger.warn("processCodeUpload() already started");
            return;
        }

        // The time by which the deployment must be completed
        long endTime = Instant.now().getEpochSecond() +
            PropertyStore.getDuration(DurationProp.LAMBDA_DEPLOYMENT_TIMEOUT).toSeconds();

        try {
            /* Step 1: Compile lambda code and create container image in ECR registry */

            // compose dockerfile containing compilation commands
            String dockerfile = BuildConfig.makeDockerfile(upload);

            // Get or create a repository in the ECR private registry.
            // Each such repository contains images related to one lambda.
            String repositoryUri = LambdaAwsResources.makeEcrRepositoryUri(upload.repoName);

            // Compose the target image URI. Docker uses this URI to determine
            // the destination of the image in the remote repository.
            String imageTag = upload.getImageTag();
            String imageUri = repositoryUri + ':' + imageTag;

            // Compile lambda code and push the resulted image to the ECR repository.
            compileDocker(dockerfile, imageUri, endTime);

            // Check the image has been successfully delivered to the ECR repository
            if (!EcrSupport.imageExists(upload.repoName, imageTag)) {
                throw new ExternalException("Created image does not exist in ECR, repoName=" + upload.repoName + ", imageTag=" + imageTag);
            }

            /* Step 2: Create or update a function in Lambda */
            EnumMap<InvocationLane, UpdateFunctionParams> paramsByLane = new EnumMap<>(InvocationLane.class);
            for (InvocationLane lane : InvocationLane.values()) {
                paramsByLane.put(lane, UpdateFunctionParams.create(upload, lane, imageUri));
            }

            lambdaFunctionDeployer.updateLambdaFunctions(paramsByLane, endTime,
                (lane, functionRef) -> persistPublishedFunction(upload, lane, functionRef));
            UpdateFunctionParams defaultParams = paramsByLane.get(InvocationLane.DEFAULT);
            upload.memory = defaultParams.memory;
            upload.timeout = defaultParams.timeout;
            upload.status = UploadStatus.COMPLETED;

            for (UpdateFunctionParams params : paramsByLane.values()) {
                lambdaLogService.createFunctionLogGroup(params.functionName);
            }

        } catch (Exception e) {
            logger.error("Exception in lambda code deployment: uploadId=" + uploadId + " : " + e);
            upload.status = UploadStatus.FAILED;
            upload.statusDate = Datetime.now();
            upload.message = e.getMessage();
        }

        /*
         * Step 3: save the upload result to the database and
         * update the lambda versions if the result is successful
         */

        if (!lambdaWorkerService.commitUpload(upload)) {
            logger.warn("processCodeUpload() result discarded after upload lost ownership: uploadId={}", upload.uploadId);
            return;
        }

        if (upload.status != UploadStatus.COMPLETED) {
            publishUploadFailure(upload);
        }
    }

    void persistPublishedFunction(LambdaCodeUpload upload, InvocationLane lane, String functionRef) {
        upload.setFunctionName(lane, functionRef);
        if (!lambdaCodeUploadDao.updateInProgressLambdaCodeUploadFunctions(upload)) {
            throw new ApplicationFailureException(
                "Cannot persist published function after upload left IN_PROGRESS, uploadId=" + upload.uploadId);
        }
    }

    public int failExpiredCodeUploads() {
        Datetime expiredBefore = Datetime.nowMinus(
            PropertyStore.getDuration(DurationProp.LAMBDA_DEPLOYMENT_TIMEOUT));

        List<LambdaCodeUpload> uploads = lambdaCodeUploadDao.getExpiredLambdaCodeUploads(
            expiredBefore, EXPIRY_BATCH_SIZE);
        int failed = 0;
        for (LambdaCodeUpload candidate : uploads) {
            LambdaCodeUpload upload = lambdaWorkerService.failExpiredUpload(
                candidate.lambdaId, candidate.uploadId, expiredBefore);
            if (upload == null) continue;
            publishUploadFailure(upload);
            failed++;
        }
        return failed;
    }

    private void publishUploadFailure(LambdaCodeUpload upload) {
        String subject = upload.lambda.lambdaName + " upload failure";
        String text = "Upload ID=" + upload.uploadId + " failed\n\n" + upload.message;

        try {
            notificationService.publish(Lambda.SNS_BUILD_ERROR_TOPIC, subject, text, Map.of(
                ATTRIBUTE_DEV_TEAM_ID, upload.lambda.devTeamId,
                ATTRIBUTE_LAMBDA_ID, upload.lambdaId));
        } catch (Exception e) {
            logger.error("Failed to publish lambda upload error notification for lambdaId=" + upload.lambdaId, e);
        }
    }

    public void purgeDeployments() {
        logger.debug(">purgeDeployments()");

        List<LambdaCodeUpload> uploads = lambdaCodeUploadDao.getLambdaCodeUploadsForCleanup();
        if ((uploads == null) || uploads.isEmpty()) {
            logger.debug("<purgeDeployments() no");
            return;
        }

        // Delete S3 objects
        HashSet<String> s3Errors = new HashSet<>(uploads.size());
        List<String> objectIds = CollectionOps.map(uploads, upload -> upload.codeObjectId);
        if (objectIds != null) S3Support.deleteObjects(Lambda.S3_BUCKET, objectIds, s3Errors);

        // Delete ECR images (imageTag = objectId)
        HashSet<String> ecrErrors = new HashSet<>(uploads.size());
        Map<String, List<String>> imagesMap = CollectionOps.group(uploads, u -> u.repoName, LambdaCodeUpload::getImageTag);
        imagesMap.forEach((repo, tags) -> EcrSupport.deleteImages(repo, tags, ecrErrors));

        StringBuilder failures = new StringBuilder();
        for (var upload : uploads) {
            failures.setLength(0);
            if (upload.codeObjectId != null) {
                if (s3Errors.contains(upload.codeObjectId)) failures.append(", S3 object");
                if (ecrErrors.contains(upload.getImageTag())) failures.append(", repository image");
            }
            for (InvocationLane lane : InvocationLane.values()) {
                String functionName = upload.getFunctionName(lane);
                if (functionName == null) continue;
                DeleteFunctionParams param = new DeleteFunctionParams(functionName);
                lambdaFunctionDeployer.deleteFunction(param);
                if (param.functionDeleted) {
                    lambdaLogService.deleteFunctionLogGroup(param.functionName);
                } else if (!param.versionDeleted) {
                    failures.append(", ").append(lane).append(" function");
                }
            }

            if (failures.isEmpty()) {
                lambdaCodeUploadDao.setLambdaCodeUploadFunctionDeletionDate(upload.uploadId, Datetime.now(), null);
            } else {
                String warning = "failed to delete" + failures.substring(1);
                lambdaCodeUploadDao.setLambdaCodeUploadFunctionDeletionDate(upload.uploadId, null, warning);
                logger.warn("Failures in purgeDeployments, uploadId=" + upload.uploadId + ": " + warning);
            }
        }

        logger.debug("<purgeDeployments()");
    }

    private static void compileDocker(String dockerFile, String imageUri, long endTime) throws Exception {
        String buildCommand = PropertyStore.getString("vf.lambda.build.command");
        if (buildCommand == null) {
            throw new Exception("Build command is not specified in the system properties");
        }

        String[] command = buildCommand.split("\\s+");
        command = Arrays.copyOf(command, command.length + 1);
        command[command.length - 1] = imageUri;
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);

        long timeoutMs = (endTime - Instant.now().getEpochSecond()) * 1000L;

        ProcessRunner.CompletedProcess cp = ProcessRunner.run(pb, dockerFile.getBytes(StandardCharsets.UTF_8), timeoutMs, 4096);
        if (!cp.isOk()) {
            throw new BuildException(cp.getLog());
        }
    }
}
