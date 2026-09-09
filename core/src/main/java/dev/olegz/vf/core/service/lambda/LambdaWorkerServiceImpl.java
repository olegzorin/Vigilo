package dev.olegz.vf.core.service.lambda;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.common.util.DateFormatUtils;
import dev.olegz.vf.core.dao.LambdaAssignmentDao;
import dev.olegz.vf.core.dao.LambdaCodeUploadDao;
import dev.olegz.vf.core.dao.LambdaDao;
import dev.olegz.vf.core.dao.LambdaStatisticsDao;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaAssignment;
import dev.olegz.vf.core.domain.lambdabuild.LambdaCodeUpload;
import dev.olegz.vf.core.domain.lambdabuild.UploadStatus;
import dev.olegz.vf.core.domain.lambdaoperation.LambdaOperationRequest;
import dev.olegz.vf.core.domain.lambdarun.LambdaError;
import dev.olegz.vf.core.domain.lambdarun.LambdaErrorAlert;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersion;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersionState;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersionStatus;
import dev.olegz.vf.core.messaging.MessageDispatcher;
import dev.olegz.vf.core.service.notification.NotificationService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service("lambdaWorkerService")
public class LambdaWorkerServiceImpl implements LambdaWorkerService {
    private static final Logger logger = LoggerFactory.getLogger(LambdaWorkerServiceImpl.class);

    private static final long ONE_HOUR_MILLIS = Duration.ofHours(1).toMillis();
    private static final String LAMBDA_ERROR_ATTRIBUTE_LAMBDA_ID = "lambdaId";
    private static final int LAMBDA_ERROR_MAX_SIZE =
        PropertyStore.getInt("vf.lambda.errorTextMaxSize", 36_000);

    private final LambdaDao lambdaDao;
    private final LambdaAssignmentDao lambdaAssignmentDao;
    private final LambdaCodeUploadDao lambdaCodeUploadDao;
    private final LambdaStatisticsDao lambdaStatisticsDao;
    private final NotificationService notificationService;

    public LambdaWorkerServiceImpl(LambdaDao lambdaDao, LambdaAssignmentDao lambdaAssignmentDao,
        LambdaCodeUploadDao lambdaCodeUploadDao, LambdaStatisticsDao lambdaStatisticsDao,
        NotificationService notificationService)
    {
        this.lambdaDao = lambdaDao;
        this.lambdaAssignmentDao = lambdaAssignmentDao;
        this.lambdaCodeUploadDao = lambdaCodeUploadDao;
        this.lambdaStatisticsDao = lambdaStatisticsDao;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LambdaCodeUpload getUploadForProcessing(int lambdaId, int uploadId) {
        if (logger.isDebugEnabled()) {
            logger.debug(">getUploadForProcessing() lambdaId=" + lambdaId + ", uploadId=" + uploadId);
        }

        Lambda lambda = lambdaDao.getLambdaForUpdate(lambdaId);
        LambdaCodeUpload upload = lambdaCodeUploadDao.getLambdaCodeUpload(uploadId);
        if ((upload == null) || (upload.lambdaId != lambdaId)) {
            logger.warn("<getUploadForProcessing() no upload: lambdaId={}, uploadId={}", lambdaId, uploadId);
            return null;
        }

        if ((lambda.codeUploadId == null) || (lambda.codeUploadId != uploadId)) {
            logger.warn("<getUploadForProcessing() no or other upload: target uploadId=" + uploadId + ", current uploadId=" + lambda.codeUploadId);
            if ((upload.status == UploadStatus.CREATED) || (upload.status == UploadStatus.IN_PROGRESS)) {
                upload.status = UploadStatus.FAILED;
                upload.statusDate = Datetime.now();
                upload.message = "Superseded by uploadId=" + lambda.codeUploadId;
                lambdaCodeUploadDao.updateLambdaCodeUpload(upload);
            }
            return null;
        }

        if (upload.status != UploadStatus.CREATED) {
            logger.warn("<getUploadForProcessing() duplicate: uploadId={}, status={}, statusDate={}",
                uploadId, upload.status, upload.statusDate);
            return null;
        }

        upload.status = UploadStatus.IN_PROGRESS;
        upload.statusDate = Datetime.now();
        upload.message = null;
        lambdaCodeUploadDao.updateLambdaCodeUpload(upload);

        logger.debug("<getUploadForProcessing()");
        return upload;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean commitUpload(LambdaCodeUpload upload) {
        if (logger.isDebugEnabled()) {
            logger.debug("|>commitUpload() " + upload);
        }

        Lambda lambda = lambdaDao.getLambdaForUpdate(upload.lambdaId);

        if ((lambda.codeUploadId == null) || (lambda.codeUploadId != upload.uploadId)) {
            logger.warn("commitUpload() upload superseded: target uploadId={}, current uploadId={}",
                upload.uploadId, lambda.codeUploadId);
            return false;
        }

        lambdaDao.updateLambdaUploadId(upload.lambdaId, null);

        if (upload.status != UploadStatus.COMPLETED) {
            upload.statusDate = Datetime.now();
            lambdaCodeUploadDao.updateLambdaCodeUpload(upload);
            return true;
        }

        LambdaVersionState lambdaVersionFamily;
        try {
            List<LambdaVersion> lambdaVersions = lambdaDao.getLambdaVersionsForUpdate(lambda.lambdaId);
            lambdaVersionFamily = LambdaVersionState.of(lambdaVersions, false);
        } catch (Exception e) {
            logger.error("commitUpload() lambdaId=" + upload.lambdaId + " : " + e.getMessage());
            upload.status = UploadStatus.FAILED;
            upload.statusDate = Datetime.now();
            upload.message = e.getMessage();
            lambdaCodeUploadDao.updateLambdaCodeUpload(upload);
            return true;
        }

        LambdaVersion lambdaVersion = lambdaVersionFamily.devVersion;
        if (lambdaVersion.lambdaVersionId != upload.lambdaVersionId) {
            logger.error("commitUpload() mismatched lambdaVersionId: uploadId=" + upload.uploadId + ", target lambdaVersionId=" + upload.lambdaVersionId + ", actual lambdaVersionId=" + lambdaVersion.lambdaVersionId);
            upload.status = UploadStatus.FAILED;
            upload.statusDate = Datetime.now();
            upload.message = "Internal error";
            lambdaCodeUploadDao.updateLambdaCodeUpload(upload);
            return true;
        }

        try {
            lambdaVersion.updateFromLambdaCodeUpload(upload);
            lambdaVersion.updateStatus(LambdaVersionStatus.TESTING);
            lambdaDao.updateLambdaVersions(lambda.lambdaId, lambdaVersionFamily.toList());
        } catch (Exception e) {
            logger.error("|commitUpload() exception when updating lambda version", e);
            upload.status = UploadStatus.FAILED;
            upload.statusDate = Datetime.now();
            upload.message = "error updating lambda version: " + e;
            lambdaCodeUploadDao.updateLambdaCodeUpload(upload);
            return true;
        }

        upload.status = UploadStatus.COMPLETED;
        upload.statusDate = Datetime.now();
        lambdaCodeUploadDao.updateLambdaCodeUpload(upload);

        MessageDispatcher.sendLambdaOperation(LambdaOperationRequest.deleteLambdaErrors(lambdaVersion.lambdaVersionId));

        logger.debug("|<commitUpload()");
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LambdaCodeUpload failExpiredUpload(int lambdaId, int uploadId, Datetime expiredBefore) {
        Lambda lambda = lambdaDao.getLambdaForUpdate(lambdaId);
        if ((lambda == null) || (lambda.codeUploadId == null) || (lambda.codeUploadId != uploadId)) return null;

        LambdaCodeUpload upload = lambdaCodeUploadDao.getLambdaCodeUpload(uploadId);
        if ((upload == null) || (upload.status != UploadStatus.IN_PROGRESS) ||
            (upload.statusDate.getTime() > expiredBefore.getTime()))
        {
            return null;
        }

        upload.status = UploadStatus.FAILED;
        upload.statusDate = Datetime.now();
        upload.message = "Deployment did not complete before the timeout";
        lambdaCodeUploadDao.updateLambdaCodeUpload(upload);
        lambdaDao.updateLambdaUploadId(lambdaId, null);
        return upload;
    }

    @Override
    public void registerLambdaError(LambdaError lambdaError) {
        if (lambdaError.message == null) {
            logger.error("registerLambdaError() empty message, {}", lambdaError);
            return;
        }

        LambdaAssignment lambdaAssignment = lambdaAssignmentDao.getLambdaAssignment(lambdaError.lambdaAssignmentId);
        if (lambdaAssignment == null) {
            logger.debug("registerLambdaError() no active lambda assignment {}", lambdaError);
            return;
        }

        LambdaVersion lambdaVersion = lambdaAssignment.lambdaVersion;
        if (!lambdaVersion.isRunnable()) {
            logger.debug("registerLambdaError() no active lambda version {}", lambdaError);
            return;
        }

        if ((lambdaVersion.functionStartDate == null) || (lambdaVersion.functionStartDate.getTime() > lambdaError.time)) {
            logger.debug("registerLambdaError() lambda code updated {}", lambdaError);
            return;
        }

        LambdaErrorAlert alert = lambdaStatisticsDao.insertLambdaError(lambdaError, lambdaAssignment);
        if (alert == null) {
            logger.debug("registerLambdaError() repeated error {}", lambdaError);
            return;
        }

        publishLambdaError(lambdaError, lambdaAssignment, alert);
    }

    private void publishLambdaError(LambdaError lambdaError, LambdaAssignment lambdaAssignment, LambdaErrorAlert alert) {
        Lambda lambda = lambdaAssignment.lambda;
        LambdaVersion lambdaVersion = lambdaAssignment.lambdaVersion;

        String subject = lambda.lambdaName + ':' + lambdaVersion.version;

        String text = alert.message +
            (alert.errorCount > 1 ? "\n\nNumber of times the error occurred: " + alert.errorCount + " in the last " +
                ((System.currentTimeMillis() - alert.errorDate.getTime()) / ONE_HOUR_MILLIS) + " hours" : "") +
            "\n\nLambda: " + lambda.lambdaName +
            "\nVersion: " + lambdaVersion.version + ", ID=" + lambdaVersion.lambdaVersionId +
            "\nDeployment Date: " + DateFormatUtils.logTimestamp(lambdaVersion.functionStartDate) +
            "\n\nLast occurrence of the error: " +
            "\n\nTimestamp: " + DateFormatUtils.logTimestamp(lambdaError.time) +
            "\nLambda Assignment ID: " + lambdaAssignment.lambdaAssignmentId +
            "\nLane: " + lambdaError.lane +
            "\nLocation ID: " + lambdaAssignment.locationId +
            "\n\nError description:\n\n" + lambdaError.message +
            (lambdaError.logs != null ? "\n\nAWS CloudWatch Logs:\n\n" + lambdaError.logs : "");

        text = StringUtils.truncate(StringUtils.trimToNull(text), LAMBDA_ERROR_MAX_SIZE);

        try {
            notificationService.publish(Lambda.SNS_RUNTIME_ERROR_TOPIC, subject, text,
                Map.of(LAMBDA_ERROR_ATTRIBUTE_LAMBDA_ID, lambdaAssignment.lambdaId));
        } catch (Exception e) {
            logger.error("registerLambdaError() failed to publish lambda error notification for " + subject, e);
        }
    }
}
