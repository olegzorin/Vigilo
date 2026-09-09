package dev.olegz.vf.core.service.lambda;

import java.util.List;
import java.util.Map;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.exception.MissingParameterException;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.common.exception.OperationNotAllowedException;
import dev.olegz.vf.core.dao.LambdaCodeUploadDao;
import dev.olegz.vf.core.dao.LambdaDao;
import dev.olegz.vf.core.dao.DevTeamDao;
import dev.olegz.vf.core.domain.lambdabuild.LambdaCodeUpload;
import dev.olegz.vf.core.domain.lambdabuild.LambdaConfig;
import dev.olegz.vf.core.domain.lambdabuild.BuildConfig;
import dev.olegz.vf.core.domain.lambdabuild.UploadStatus;
import dev.olegz.vf.core.domain.lambdaoperation.LambdaOperationRequest;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersion;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersionState;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersionStatus;
import dev.olegz.vf.core.messaging.MessageDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service("lambdaManagementService")
public class LambdaManagementServiceImpl implements LambdaManagementService {
    private static final Logger logger = LoggerFactory.getLogger(LambdaManagementServiceImpl.class);

    private final LambdaDao lambdaDao;
    private final LambdaCodeUploadDao lambdaCodeUploadDao;
    private final DevTeamDao devTeamDao;

    public LambdaManagementServiceImpl(LambdaDao lambdaDao, LambdaCodeUploadDao lambdaCodeUploadDao, DevTeamDao devTeamDao) {
        this.lambdaDao = lambdaDao;
        this.lambdaCodeUploadDao = lambdaCodeUploadDao;
        this.devTeamDao = devTeamDao;
    }

    @Override
    public int createLambda(int userId, String lambdaName, int devTeamId, String description, Map<String, Object> metadata) {
        if (logger.isDebugEnabled()) {
            logger.debug(">createLambda() userId=" + userId + ", lambdaName=" + lambdaName + ", devTeamId=" + devTeamId + ", description=" + description + ", metadata=" + metadata);
        }
        if (!devTeamDao.checkDevTeamMember(devTeamId, userId)) {
            throw new ObjectNotFoundException("Dev team " + devTeamId + " not found");
        }
        Lambda lambda = new Lambda();
        lambda.lambdaName = lambdaName;
        lambda.description = description;
        lambda.metadata = metadata;
        lambda.createdAt = Datetime.now();
        lambda.devTeamId = devTeamId;
        lambdaDao.insertLambda(lambda);

        logger.debug("<createLambda()");
        return lambda.lambdaId;
    }

    @Override
    public void updateLambda(int userId, int lambdaId, String description, Map<String, Object> metadata) {
        if (logger.isDebugEnabled()) {
            logger.debug(">updateLambda() userId=" + userId + ", lambdaId=" + lambdaId + ", description=" + description + ", metadata=" + metadata);
        }

        Lambda lambda = lambdaDao.getLambda(lambdaId);
        if (lambda == null) throw new ObjectNotFoundException("Lambda not found");

        if (!devTeamDao.checkDevTeamMember(lambda.devTeamId, userId)) {
            throw new ObjectNotFoundException("Lambda not found");
        }

        boolean updated = false;
        if (description != null && !description.equals(lambda.description)) {
            lambda.description = description;
            updated = true;
        }

        if (metadata != null && !metadata.equals(lambda.metadata)) {
            lambda.metadata = metadata;
            updated = true;
        }

        if (!updated) {
            logger.debug("<updateLambda() nothing to update");
            return;
        }

        lambdaDao.updateLambda(lambda);
        logger.debug("<updateLambda()");
    }

    @Override
    public List<Lambda> getLambdas(int userId, Integer devTeamId) {
        return lambdaDao.getLambdasByDeveloper(userId, devTeamId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LambdaVersion setLambdaConfiguration(int userId, int lambdaId, LambdaConfig newConfig) {
        if (logger.isDebugEnabled()) {
            logger.debug(">setLambdaConfiguration() userId=" + userId + ", lambdaId=" + lambdaId + '\n' + newConfig);
        }

        newConfig.checkTriggers();

        if (newConfig.bump == null) {
            throw new MissingParameterException("Missing version bump level");
        }

        Lambda lambda = getLambdaForUpdate(lambdaId, userId);

        List<LambdaVersion> lambdaVersions = lambdaDao.getLambdaVersionsForUpdate(lambda.lambdaId);
        LambdaVersion lambdaVersion = LambdaVersionState.of(lambdaVersions, false).devVersion;
        if (lambdaVersion == null) {
            lambdaVersion = new LambdaVersion();
            lambdaVersion.lambdaId = lambda.lambdaId;
            lambdaVersion.createdBy = userId;
            lambdaVersion.createdAt = Datetime.now();
            lambdaVersion.statusDate = lambdaVersion.createdAt;
            lambdaVersion.status = LambdaVersionStatus.DRAFT;
            lambdaVersion.latest = LambdaVersion.LATEST_NONE;
            // Assign the internal creation-order sequence number from the lambda's monotonic counter.
            // Unlike the semantic version, it advances on every new version and never regresses, so
            // it is a stable, gap-free internal ordinal. The lambda row is locked FOR UPDATE, so the
            // read-increment-write is safe.
            lambdaVersion.sequenceNumber = ++lambda.maxSequence;
            lambdaDao.updateLambdaMaxSequence(lambda.lambdaId, lambda.maxSequence);
        }

        if (lambdaVersion.status == LambdaVersionStatus.DISCARDED) {
            lambdaVersion.updateStatus(LambdaVersionStatus.DRAFT);
        }

        // Derive the version from the lambda's monotonic high-water mark, which advances only on publish.
        // The dev version's own (unpublished) number is therefore not part of the basis, so repeated
        // saves are idempotent and a changed bump level simply recomputes the number.
        lambdaVersion.version = newConfig.bump.next(lambda.maxVersion);
        lambdaVersion.updateConfiguration(newConfig);
        lambdaDao.insertLambdaVersionConfig(lambdaVersion);

        logger.debug("<setLambdaConfiguration()");
        return lambdaVersion;
    }

    @Override
    public List<LambdaVersion> getLambdaVersions(int userId, int lambdaId, LambdaVersionStatus[] statuses, String version) {
        Lambda lambda = lambdaDao.getLambdaByIdAndUser(lambdaId, userId);
        if (lambda == null) throw new ObjectNotFoundException("Lambda not found");

        return lambdaDao.getLambdaVersions(lambdaId, version, statuses);
    }

    private Lambda getLambdaForUpdate(int lambdaId, int userId) {
        Lambda lambda = lambdaDao.getLambdaForUpdate(lambdaId, userId);
        if (lambda == null) {
            throw new ObjectNotFoundException("Lambda not found");
        }

        if (lambda.codeUploadId != null) {
            LambdaCodeUpload upload = lambdaCodeUploadDao.getLambdaCodeUpload(lambda.codeUploadId);
            if ((upload != null) && upload.inProgress()) {
                throw new OperationNotAllowedException("Code deployment is in progress");
            }
        }
        return lambda;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void promoteTestVersionToProduction(int userId, int lambdaId) {
        if (logger.isDebugEnabled()) {
            logger.debug(">promoteTestVersionToProduction() userId=" + userId + ", lambdaId=" + lambdaId);
        }
        Lambda lambda = getLambdaForUpdate(lambdaId, userId);
        Integer lambdaVersionIdToDeleteErrors = null;

        LambdaVersionState lambdaVersionState = LambdaVersionState.of(lambdaDao.getLambdaVersionsForUpdate(lambda.lambdaId), false);
        LambdaVersion devVersion = lambdaVersionState.devVersion;
        LambdaVersion pubVersion = lambdaVersionState.pubVersion;

        if (devVersion == null) {
            throw new OperationNotAllowedException("No development version to publish");
        }
        if (devVersion.status != LambdaVersionStatus.TESTING) {
            throw new OperationNotAllowedException("Cannot publish development version until it is under test");
        }
        devVersion.updateStatus(LambdaVersionStatus.PRODUCTION);

        // Advance the lambda's high-water mark. It only ever increases (the published number was
        // computed as bump.next(maxVersion)), so a later rollback leaves it untouched and the
        // next development version is still numbered above every version ever published.
        lambda.maxVersion = devVersion.version;
        lambdaDao.updateLambdaMaxVersion(lambda.lambdaId, lambda.maxVersion);

        if (pubVersion != null) {
            pubVersion.updateStatus(LambdaVersionStatus.FALLBACK);
            lambdaVersionIdToDeleteErrors = pubVersion.lambdaVersionId;

            LambdaVersion fallbackVersion = lambdaVersionState.fallbackVersion;
            if (fallbackVersion != null) {
                fallbackVersion.updateStatus(LambdaVersionStatus.ARCHIVED);
            }
        }

        lambdaDao.updateLambdaVersions(lambda.lambdaId, lambdaVersionState.toList());
        deleteLambdaErrors(lambdaVersionIdToDeleteErrors);
        logger.debug("<promoteTestVersionToProduction() {}", lambdaId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void discardTestVersion(int userId, int lambdaId) {
        if (logger.isDebugEnabled()) {
            logger.debug(">discardTestVersion() userId=" + userId + ", lambdaId=" + lambdaId);
        }
        Lambda lambda = getLambdaForUpdate(lambdaId, userId);

        LambdaVersionState lambdaVersionFamily = LambdaVersionState.of(lambdaDao.getLambdaVersionsForUpdate(lambda.lambdaId), false);
        LambdaVersion devVersion = lambdaVersionFamily.devVersion;

        if (devVersion == null) {
            throw new OperationNotAllowedException("Lambda doesn't have a development version");
        }
        if (devVersion.status == LambdaVersionStatus.DISCARDED) return;

        if (!devVersion.status.canChangeTo(LambdaVersionStatus.DISCARDED)) {
            throw new OperationNotAllowedException("Specified status change is not available. See the Lambda version lifecycle documentation.");
        }

        if (devVersion.getFunctionName(InvocationLane.DEFAULT) == null) {
            throw new OperationNotAllowedException("Any status change is not possible until the lambda is deployed to AWS Lambda");
        }

        devVersion.updateStatus(LambdaVersionStatus.DISCARDED);
        lambdaDao.updateLambdaVersions(lambda.lambdaId, lambdaVersionFamily.toList());
        deleteLambdaErrors(devVersion.lambdaVersionId);

        logger.debug("<discardTestVersion()");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rollbackProductionVersion(int userId, int lambdaId) {
        logger.debug(">rollbackProductionVersion() userId=" + userId + ", lambdaId=" + lambdaId);

        Lambda lambda = getLambdaForUpdate(lambdaId, userId);

        LambdaVersionState lambdaVersionFamily = LambdaVersionState.of(lambdaDao.getLambdaVersionsForUpdate(lambda.lambdaId), false);
        LambdaVersion pubVersion = lambdaVersionFamily.pubVersion;

        if (pubVersion == null) {
            throw new OperationNotAllowedException("Cannot rollback production version: no such version");
        }
        LambdaVersion fallbackVersion = lambdaVersionFamily.fallbackVersion;
        if (fallbackVersion == null) {
            throw new OperationNotAllowedException("Cannot rollback production version: no fallback version available");
        }
        if ((fallbackVersion.codeUploadId == null) || (fallbackVersion.codeUploadId == 0)) {
            logger.error("Fallback version does not contain the code upload reference, lambdaVersionId=" + fallbackVersion.lambdaVersionId + ", codeUploadId=" + fallbackVersion.codeUploadId);
            fallbackVersion.updateStatus(LambdaVersionStatus.ARCHIVED);
            throw new OperationNotAllowedException("Cannot rollback production version: no fallback version available");
        }
        LambdaCodeUpload upload = lambdaCodeUploadDao.getLambdaCodeUpload(fallbackVersion.codeUploadId);
        if (upload == null) {
            logger.error("Cannot find upload data for fallback version, lambdaVersionId=" + fallbackVersion.lambdaVersionId + ", codeUploadId=" + fallbackVersion.codeUploadId);
            fallbackVersion.updateStatus(LambdaVersionStatus.ARCHIVED);
            throw new OperationNotAllowedException("Cannot rollback production version: no fallback version available");
        }
        fallbackVersion.updateFromLambdaCodeUpload(upload);
        fallbackVersion.updateStatus(LambdaVersionStatus.PRODUCTION);

        pubVersion.updateStatus(LambdaVersionStatus.ARCHIVED);

        lambdaDao.updateLambdaVersions(lambda.lambdaId, lambdaVersionFamily.toList());
        deleteLambdaErrors(pubVersion.lambdaVersionId);
        logger.debug("<rollbackProductionVersion()");
    }

    private void deleteLambdaErrors(Integer lambdaVersionId) {
        if (lambdaVersionId == null) return;

        if (!TransactionSynchronizationManager.isActualTransactionActive() ||
            !TransactionSynchronizationManager.isSynchronizationActive())
        {
            throw new IllegalStateException("Lambda error deletion must be scheduled inside a transaction");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                MessageDispatcher.sendLambdaOperation(LambdaOperationRequest.deleteLambdaErrors(lambdaVersionId));
            }
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Lambda deleteLambdaActiveVersions(int userId, int lambdaId) {
        if (logger.isDebugEnabled()) {
            logger.debug(">deleteLambdaActiveVersions() userId=" + userId + ", lambdaId=" + lambdaId);
        }
        Lambda lambda = getLambdaForUpdate(lambdaId, userId);
        lambdaDao.deleteLambdaActiveVersions(lambdaId);

        logger.debug("<deleteLambdaActiveVersions()");
        return lambda;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LambdaCodeUpload uploadLambdaCode(int userId, int lambdaId, BuildConfig buildConfig) {
        if (logger.isDebugEnabled()) {
            logger.debug(">uploadLambdaCode() userId=" + userId + ", lambdaId=" + lambdaId + ", " + buildConfig);
        }

        Lambda lambda = getLambdaForUpdate(lambdaId, userId);
        List<LambdaVersion> lambdaVersions = lambdaDao.getLambdaVersionsForUpdate(lambdaId);
        LambdaVersion lambdaVersion = LambdaVersionState.of(lambdaVersions, false).devVersion;
        if (lambdaVersion == null) {
            throw new OperationNotAllowedException("Cannot start code upload: no development version");
        }
        if (!lambdaVersion.canUpdate()) {
            throw new OperationNotAllowedException("Cannot upload code while the development version is in status " + lambdaVersion.status);
        }

        LambdaCodeUpload upload = LambdaCodeUpload.create(buildConfig, lambdaId, lambdaVersion.lambdaVersionId, userId);

        lambdaCodeUploadDao.insertLambdaCodeUpload(upload);
        if (lambda.codeUploadId != null) {
            LambdaCodeUpload previousUpload = lambdaCodeUploadDao.getLambdaCodeUpload(lambda.codeUploadId);
            if ((previousUpload != null) &&
                ((previousUpload.status == UploadStatus.CREATED) || (previousUpload.status == UploadStatus.IN_PROGRESS)))
            {
                previousUpload.status = UploadStatus.FAILED;
                previousUpload.statusDate = Datetime.now();
                previousUpload.message = "Superseded by uploadId=" + upload.uploadId;
                lambdaCodeUploadDao.updateLambdaCodeUpload(previousUpload);
            }
        }
        lambdaDao.updateLambdaUploadId(lambdaId, upload.uploadId);

        logger.debug("<uploadLambdaCode()");
        return upload;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failLambdaCodeUpload(LambdaCodeUpload upload, String message) {
        upload.status = UploadStatus.FAILED;
        upload.statusDate = Datetime.now();
        upload.message = message;
        lambdaCodeUploadDao.updateLambdaCodeUpload(upload);
    }

    @Override
    public LambdaCodeUpload getLambdaCodeUpload(int userId, int lambdaId, int uploadId) {
        Lambda lambda = lambdaDao.getLambdaByIdAndUser(lambdaId, userId);
        if (lambda == null) {
            throw new ObjectNotFoundException("Lambda not found");
        }
        LambdaCodeUpload lambdaCodeUpload = lambdaCodeUploadDao.getLambdaCodeUpload(lambdaId, uploadId);
        if (lambdaCodeUpload == null) {
            throw new ObjectNotFoundException("No uploads found");
        }
        return lambdaCodeUpload;
    }

}
