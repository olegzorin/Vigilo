package dev.olegz.vf.core.service.lambda;

import dev.olegz.vf.registry.domain.account.LocationType;
import dev.olegz.vf.registry.service.account.AccessService;
import java.util.List;

import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.core.dao.*;
import dev.olegz.vf.registry.dao.LocationDao;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.domain.account.Location;
import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaAssignment;
import dev.olegz.vf.core.domain.lambdarun.LambdaKeyInput;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersion;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("LambdaAssignmentService")
public class LambdaAssignmentServiceImpl implements LambdaAssignmentService {
    private static final Logger logger = LoggerFactory.getLogger(LambdaAssignmentServiceImpl.class);

    private final AccessService access;
    private final LambdaDao lambdaDao;
    private final LambdaAssignmentDao lambdaAssignmentDao;
    private final LambdaAssignmentCleanupService cleanupService;
    private final LocationDao locationDao;
    private final OrganizationDao organizationDao;
    private final DevTeamDao teamDao;
    private final LambdaClientService lambdaClientService;


    public LambdaAssignmentServiceImpl(LambdaDao lambdaDao, LambdaAssignmentDao lambdaAssignmentDao,
        LambdaAssignmentCleanupService cleanupService,
        LocationDao locationDao, OrganizationDao organizationDao,
        DevTeamDao teamDao, LambdaClientService lambdaClientService, AccessService access) {
        this.access = access;
        this.lambdaDao = lambdaDao;
        this.lambdaAssignmentDao = lambdaAssignmentDao;
        this.cleanupService = cleanupService;
        this.locationDao = locationDao;
        this.organizationDao = organizationDao;
        this.teamDao = teamDao;
        this.lambdaClientService = lambdaClientService;
    }

    @Override
    @Transactional
    public int createLambdaAssignment(User user, int lambdaId, int locationId, boolean testing) {
        if (logger.isDebugEnabled()) {
            logger.debug(">createLambdaAssignment() lambdaId=" + lambdaId + ", locationId=" + locationId);
        }

        Lambda lambda = lambdaDao.getLambda(lambdaId);
        if (lambda == null) {
            throw new ObjectNotFoundException("Lambda not found");
        }

        Location location = access.requireLocation(user, locationId);
        if (location.locationType == LocationType.OPERATIONAL && testing) {
            throw new AccessDeniedException("Test versions cannot run at operational locations");
        }
        if (!access.isAdmin(user) && !teamDao.checkDevTeamMember(lambda.devTeamId, user.userId)) {
            throw new AccessDeniedException("Testing requires membership in the lambda team");
        }
        if (testing && !teamDao.checkDevTeamMember(lambda.devTeamId, user.userId)) {
            var team = teamDao.getDevTeam(lambda.devTeamId);
            if (team == null || team.organizationId != user.organizationId) throw new AccessDeniedException("Cannot test another organization's lambda");
        }
        List<LambdaVersion> versions = lambdaDao.getLambdaVersions(lambdaId, null, LambdaVersionStatus.ALL_ACTIVE);
        if (!testing && (versions == null || versions.stream().noneMatch(v -> v.published && v.isRunnable()))) {
            throw new AccessDeniedException("A production version is required");
        }
        LambdaAssignment lambdaAssignment = new LambdaAssignment(lambdaId, locationId, testing);
        lambdaAssignmentDao.insertLambdaAssignment(lambdaAssignment);
        lambdaClientService.sendResetEvent(lambdaAssignment);

        int lambdaAssignmentId = lambdaAssignment.lambdaAssignmentId;

        if (logger.isDebugEnabled()) {
            logger.debug("<createLambdaAssignment() lambdaAssignmentId=" + lambdaAssignmentId);
        }
        return lambdaAssignmentId;
    }

    @Override
    public LambdaAssignment getLambdaAssignment(User user, int lambdaAssignmentId) {
        LambdaAssignment lambdaAssignment = lambdaAssignmentDao.getLambdaAssignment(lambdaAssignmentId);
        if (lambdaAssignment == null) {
            throw new ObjectNotFoundException("Lambda assignment not found");
        }

        requireAssignmentLocationAccess(user, lambdaAssignment);
        Lambda lambda = lambdaAssignment.lambda;

        List<LambdaVersion> lambdaVersions = lambdaDao.getLambdaVersions(lambda.lambdaId, null, LambdaVersionStatus.ALL_ACTIVE);
        lambda.lambdaVersions = lambdaVersions != null ? lambdaVersions : List.of();
        LambdaVersion lambdaVersion = lambdaAssignment.testing
            ? lambda.getLatestRunnableLambdaVersion()
            : lambda.getPublicLambdaVersion();
        if (lambdaVersion != null) {
            lambdaAssignment.lambdaVersion = lambdaVersion;
            lambdaAssignment.lambdaVersionId = lambdaVersion.lambdaVersionId;
        }
        return lambdaAssignment;
    }

    @Override
    public LambdaKeyInput generateLambdaApiKey(User user, int lambdaAssignmentId) {
        return lambdaClientService.generateLambdaApiKey(user, lambdaAssignmentId);
    }

    @Override
    public List<LambdaAssignment> getLambdaAssignmentsForLambda(User user, int lambdaId) {
        Lambda lambda = lambdaDao.getLambda(lambdaId);
        if (lambda == null) {
            throw new ObjectNotFoundException("Lambda not found");
        }

        List<LambdaAssignment> assignments = lambdaAssignmentDao.getLambdaAssignments(lambdaId, null, false);
        if (assignments == null) return List.of();
        return assignments.stream().filter(assignment -> canAccessAssignment(user, assignment)).toList();
    }

    @Override
    public List<LambdaAssignment> getLambdaAssignmentsForLocation(User user, int locationId) {
        access.requireLocation(user, locationId);
        List<LambdaAssignment> assignments = lambdaAssignmentDao.getLambdaAssignments(null, locationId, false);
        if (assignments == null) return List.of();
        return assignments.stream().filter(assignment -> canAccessAssignment(user, assignment)).toList();
    }

    private boolean canAccessAssignment(User user, LambdaAssignment assignment) {
        Location location = locationDao.getOrganizationLocation(user.organizationId, assignment.locationId);
        if (!access.canAccess(user, location)) return false;
        Lambda lambda = lambdaDao.getLambda(assignment.lambdaId);
        return access.isAdmin(user) || (lambda != null && teamDao.checkDevTeamMember(lambda.devTeamId, user.userId));
    }


    private void cancelLambdaAssignmentInternal(int lambdaAssignmentId) {
        if (logger.isDebugEnabled()) {
            logger.debug("|>cancelLambdaAssignment() lambdaAssignmentId=" + lambdaAssignmentId);
        }

        if (!lambdaAssignmentDao.markLambdaAssignmentDeleted(lambdaAssignmentId)) {
            logger.debug("|<cancelLambdaAssignment() not deleted lambdaAssignmentId=" + lambdaAssignmentId);
            return;
        }
        cleanupService.enqueueLambdaAssignmentCleanup(lambdaAssignmentId);

        if (logger.isDebugEnabled()) {
            logger.debug("|<cancelLambdaAssignment() cancelled, lambdaAssignmentId=" + lambdaAssignmentId);
        }
    }

    @Override
    @Transactional
    public void cancelLambdaAssignment(User user, int lambdaAssignmentId) {
        if (logger.isDebugEnabled()) {
            logger.debug(">deleteLambdaAssignment() userId=" + user.userId + ", lambdaAssignmentId=" + lambdaAssignmentId);
        }

        LambdaAssignment lambdaAssignment = lambdaAssignmentDao.getLambdaAssignment(lambdaAssignmentId);
        requireAssignmentLocationAccess(user, lambdaAssignment);
        cancelLambdaAssignmentInternal(lambdaAssignmentId);

        if (logger.isDebugEnabled()) {
            logger.debug("<deleteLambdaAssignment() userId=" + user.userId + ", lambdaAssignmentId=" + lambdaAssignmentId);
        }
    }

    @Override
    @Transactional
    public void setLambdaAssignmentEnabled(User user, int lambdaAssignmentId, boolean enabled)
    {
        if (logger.isDebugEnabled()) {
            logger.debug(">setLambdaAssignmentEnabled() lambdaAssignmentId=" + lambdaAssignmentId + ", enabled=" + enabled);
        }

        LambdaAssignment lambdaAssignment = lambdaAssignmentDao.getLambdaAssignment(lambdaAssignmentId);
        requireAssignmentLocationAccess(user, lambdaAssignment);

        if (enabled && lambdaAssignment.testing
            && access.requireLocation(user, lambdaAssignment.locationId).locationType == LocationType.OPERATIONAL) {
            throw new AccessDeniedException("Test versions cannot run at operational locations");
        }
        boolean wasActive = lambdaAssignment.checkActive();
        lambdaAssignment.setEnabled(enabled);
        if (!lambdaAssignmentDao.updateLambdaAssignment(lambdaAssignment)) {
            throw new ObjectNotFoundException("Lambda assignment not found");
        }
        if (!wasActive && lambdaAssignment.checkActive()) {
            lambdaClientService.sendResetEvent(lambdaAssignment);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("<setLambdaAssignmentEnabled() lambdaAssignmentId=" + lambdaAssignmentId);
        }
    }

    @Override
    @Transactional
    public void cancelAssignmentsForLocation(User user, int locationId) {
        if (logger.isDebugEnabled()) {
            logger.debug("cancelAssignmentsForLocation() locationId=" + locationId);
        }
        requireLocationAccess(user, locationId);
        for (LambdaAssignment assignment : getLambdaAssignmentsForLocation(user, locationId)) {
            cancelLambdaAssignmentInternal(assignment.lambdaAssignmentId);
        }
    }

    @Override
    @Transactional
    public void deleteAssignmentsForLocation(int locationId) {
        List<Integer> lambdaAssignmentIds = lambdaAssignmentDao.getLambdaAssignmentIdsForLocation(locationId);
        if (lambdaAssignmentIds != null) {
            for (int lambdaAssignmentId : lambdaAssignmentIds) {
                cancelLambdaAssignmentInternal(lambdaAssignmentId);
            }
        }
    }

    @Override
    @Transactional
    public void cancelAssignmentsForLambda(User user, int lambdaId) {
        if (logger.isDebugEnabled()) {
            logger.debug(">cancelAssignmentsForLambda() lambdaId=" + lambdaId);
        }
        Lambda lambda = lambdaDao.getLambda(lambdaId);
        if (lambda == null) {
            throw new ObjectNotFoundException("Lambda not found");
        }

        List<LambdaAssignment> assignments = getLambdaAssignmentsForLambda(user, lambdaId);
        for (LambdaAssignment assignment : assignments) {
            cancelLambdaAssignmentInternal(assignment.lambdaAssignmentId);
        }
        logger.debug("<cancelAssignmentsForLambda()");
    }

    private void requireAssignmentLocationAccess(User user, LambdaAssignment lambdaAssignment) {
        if (lambdaAssignment == null) {
            throw new ObjectNotFoundException("Lambda assignment not found");
        }
        if (!canAccessAssignment(user, lambdaAssignment)) throw new AccessDeniedException("Lambda assignment is not accessible");
    }

    private void requireLocationAccess(User user, int locationId) {
        access.requireLocation(user, locationId);
    }

}
