package dev.olegz.vf.core.dao.impl;

import java.util.List;

import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.exception.DuplicateEntityException;
import dev.olegz.vf.common.util.CollectionOps;
import dev.olegz.vf.registry.cache.CacheNames;
import dev.olegz.vf.core.dao.LambdaDao;
import dev.olegz.vf.core.dao.mapper.LambdaVersionMapper;
import dev.olegz.vf.core.domain.lambdaoperation.LambdaOperationRequest;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersion;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersionState;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersionStatus;
import dev.olegz.vf.core.messaging.MessageDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class LambdaDaoImpl implements LambdaDao {
    private static final Logger logger = LoggerFactory.getLogger(LambdaDaoImpl.class);

    private final LambdaVersionMapper mapper;

    public LambdaDaoImpl(LambdaVersionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertLambda(Lambda lambda) {
        logger.debug("insertLambda() {}", lambda);

        try {
            mapper.insertLambda(lambda);
        } catch (DuplicateKeyException e) {
            throw new DuplicateEntityException("Lambda already exists");
        }
    }

    @Override
    public void updateLambda(Lambda lambda) {
        logger.debug("updateLambda() {}", lambda);
        mapper.updateLambda(lambda);
    }

    @Override
    public void updateLambdaUploadId(int lambdaId, Integer uploadId) {
        logger.debug("updateLambdaUploadId() lambdaId={}, uploadId={}", lambdaId, uploadId);
        mapper.updateLambdaUploadId(lambdaId, uploadId);
    }

    @Override
    public void updateLambdaMaxVersion(int lambdaId, String maxVersion) {
        logger.debug("updateLambdaMaxVersion() lambdaId={}, maxVersion={}", lambdaId, maxVersion);
        mapper.updateLambdaMaxVersion(lambdaId, maxVersion);
    }

    @Override
    public void updateLambdaMaxSequence(int lambdaId, int maxSequence) {
        logger.debug("updateLambdaMaxSequence() lambdaId={}, maxSequence={}", lambdaId, maxSequence);
        mapper.updateLambdaMaxSequence(lambdaId, maxSequence);
    }

    @Override
    public Lambda getLambda(int lambdaId) {
        return mapper.selectLambdaById(lambdaId);
    }

    @Override
    public Lambda getLambdaByIdAndUser(int lambdaId, int userId) {
        return mapper.selectLambdaByIdAndUser(lambdaId, userId);
    }

    @Override
    public List<Lambda> getLambdasByDeveloper(int userId, Integer devTeamId) {
        return mapper.selectLambdasByDeveloper(userId, devTeamId);
    }

    @Override
    public Lambda getLambdaForUpdate(int lambdaId) {
        return mapper.selectLambdaForUpdate(lambdaId);
    }

    @Override
    public Lambda getLambdaForUpdate(int lambdaId, int userId) {
        return mapper.selectLambdaForUpdateByIdAndUser(lambdaId, userId);
    }

    @Override
    public List<LambdaVersion> getLambdaVersions(int lambdaId, String version, LambdaVersionStatus[] statuses) {
        if ((statuses != null) && (statuses.length == 0)) statuses = null;
        return mapper.selectLambdaVersions(lambdaId, statuses, version);
    }

    @Override
    public List<LambdaVersion> getLambdaVersionsForUpdate(int lambdaId) {
        return mapper.selectLambdaVersions(lambdaId, LambdaVersionStatus.ALL_WORKABLE, null);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.LAMBDA_RUNTIME_ASSIGNMENTS, allEntries = true)
    public void insertLambdaVersionConfig(LambdaVersion lambdaVersion) {
        logger.debug(">putLambdaVersionConfig() {}", lambdaVersion);

        mapper.insertLambdaVersion(lambdaVersion);
        mapper.insertLambdaActiveVersion(lambdaVersion);
        logger.debug("<putLambdaVersionConfig()");
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.LAMBDA_RUNTIME_ASSIGNMENTS, allEntries = true)
    public void updateLambdaVersions(int lambdaId, List<LambdaVersion> lambdaVersions) {
        if (logger.isDebugEnabled()) {
            logger.debug(">updateLambdaVersions() lambdaId=" + lambdaId + ", lambdaVersions=" + lambdaVersions);
        }

        if ((lambdaVersions == null) || lambdaVersions.isEmpty()) {
            throw new ApplicationFailureException("No lambda versions");
        }

        LambdaVersionState state = LambdaVersionState.of(lambdaVersions, true);

        // Update the developer version

        if (state.devVersion == null) {
            mapper.clearNonPublicLambdaVersionActive(lambdaId);
        } else {
            if (state.devVersion.isRunnable() && (state.pubVersion != null)) {
                // Update the public version before the dev version
                // to avoid violating UNIQUE(lambda_id, latest_status)
                mapper.insertLambdaActiveVersion(state.pubVersion);
                mapper.updateLambdaVersion(state.pubVersion);
                // Stop processing the pub version
                state.pubVersion = null;
            }
            mapper.insertLambdaActiveVersion(state.devVersion);
            mapper.updateLambdaVersion(state.devVersion);
        }

        // Update the publicly available version (it is the latest if it is not null)

        if (state.pubVersion != null) {
            mapper.insertLambdaActiveVersion(state.pubVersion);
            mapper.updateLambdaVersion(state.pubVersion);
        }

        if (state.archivedVersions != null) {
            for (LambdaVersion lambdaVersion : state.archivedVersions) {
                archiveLambdaVersion(lambdaVersion);
            }
        }

        // Update the version replaced by a newer public version
        if ((state.fallbackVersion != null) && state.fallbackVersion.published) {
            mapper.updateLambdaVersion(state.fallbackVersion);
        }

        logger.debug("<updateLambdaVersions()");
    }

    private void archiveLambdaVersion(LambdaVersion lambdaVersion) {
        if (logger.isDebugEnabled()) {
            logger.debug(">archiveLambdaVersion() lambdaVersion=" + lambdaVersion);
        }

        mapper.updateLambdaVersion(lambdaVersion);
        MessageDispatcher.sendLambdaOperation(LambdaOperationRequest.deleteLambdaErrors(lambdaVersion.lambdaVersionId));

        logger.debug("<archiveLambdaVersion()");
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.LAMBDA_RUNTIME_ASSIGNMENTS, allEntries = true)
    public void deleteLambdaActiveVersions(int lambdaId) {
        if (logger.isDebugEnabled()) {
            logger.debug(">deleteLambdaActiveVersions() lambdaId=" + lambdaId);
        }
        List<LambdaVersion> lambdaVersions = mapper.selectLambdaVersions(lambdaId, LambdaVersionStatus.ALL_WORKABLE, null);
        if (lambdaVersions != null) {
            for (LambdaVersion lambdaVersion : lambdaVersions) {
                lambdaVersion.status = LambdaVersionStatus.ARCHIVED;
                lambdaVersion.statusDate = Datetime.now();
                archiveLambdaVersion(lambdaVersion);
            }
        }
        mapper.deleteLambdaActiveVersions(lambdaId);

        logger.debug("<deleteLambdaActiveVersions()");
    }

    @Override
    // For tests only
    @Transactional
    @CacheEvict(cacheNames = CacheNames.LAMBDA_RUNTIME_ASSIGNMENTS, allEntries = true)
    public void deleteLambda(String lambdaName, int userId, int devTeamId) {
        logger.debug(">deleteLambda() {}", lambdaName);

        List<Lambda> lambdas = mapper.selectLambdasByDeveloper(userId, devTeamId);

        Lambda lambda = CollectionOps.findAny(lambdas, b -> b.lambdaName.equals(lambdaName));

        if (lambda == null) {
            logger.debug("<deleteLambda() not found lambdaName={}", lambdaName);
            return;
        }

        int lambdaId = lambda.lambdaId;

        List<Integer> lambdaAssignmentIds = mapper.selectLambdaAssignmentIdsByLambdaId(lambdaId);

        if (lambdaAssignmentIds != null) {
            for (int lambdaAssignmentId : lambdaAssignmentIds) {
                mapper.deleteLambdaAssignmentVariable(lambdaAssignmentId, null);
                mapper.deleteLambdaAssignmentRun(lambdaAssignmentId);
                while (mapper.deleteLambdaPendingInputs(lambdaAssignmentId, 100) >= 100) ;
                mapper.deleteLambdaAssignment(lambdaAssignmentId);

                if (logger.isDebugEnabled()) logger.debug("deleteLambda() deleted lambdaAssignmentId=" + lambdaAssignmentId + ", lambdaId=" + lambdaId);
            }
        }

        mapper.deleteLambdaActiveVersions(lambdaId);
        mapper.deleteLambdaVersions(lambdaId);

        mapper.deleteLambdaCodeUploads(lambdaId);
        mapper.deleteLambda(lambdaId);

        if (logger.isDebugEnabled()) logger.debug("<deleteLambda() lambdaId=" + lambdaId);
    }

}
