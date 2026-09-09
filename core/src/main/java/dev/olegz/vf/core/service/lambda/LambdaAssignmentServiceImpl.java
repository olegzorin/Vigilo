package dev.olegz.vf.core.service.lambda;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.core.dao.*;
import dev.olegz.vf.registry.dao.LocationDao;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.domain.account.Location;
import dev.olegz.vf.registry.domain.account.Organization;
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
        DevTeamDao teamDao, LambdaClientService lambdaClientService) {
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

        Location userLocation = locationDao.getLocationByUser(user);
        boolean locationAccess = (userLocation != null) && (userLocation.locationId == lambdaAssignment.locationId);
        Lambda lambda = lambdaAssignment.lambda;
        if (!locationAccess) {
            if (!lambdaAssignment.testing) {
                throw new AccessDeniedException("Lambda assignment is not accessible");
            }

            if (!teamDao.checkDevTeamMember(lambda.devTeamId, user.userId)) {
                throw new AccessDeniedException("Lambda assignment is not accessible");
            }
        }

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

        List<LambdaAssignment> assignments = new ArrayList<>();

        Location location = locationDao.getLocationByUser(user);
        if (location != null) {
            List<LambdaAssignment> locationAssignments = lambdaAssignmentDao.getLambdaAssignments(lambdaId, location.locationId, false);
            if (locationAssignments != null) assignments.addAll(locationAssignments);
        }

        boolean isDeveloper = teamDao.checkDevTeamMember(lambda.devTeamId, user.userId);
        if (isDeveloper) {
            List<LambdaAssignment> testingAssignments = lambdaAssignmentDao.getLambdaAssignments(lambdaId, null, true);
            if (testingAssignments != null) assignments.addAll(testingAssignments);
        }

        Set<Integer> locationIds = new HashSet<>();
        List<LambdaAssignment> uniqueAssignments = new ArrayList<>(assignments.size());
        for (LambdaAssignment assignment : assignments) {
            if (locationIds.add(assignment.locationId)) {
                uniqueAssignments.add(assignment);
            }
        }
        return uniqueAssignments;
    }

    @Override
    public List<LambdaAssignment> getLambdaAssignmentsForLocation(User user, int locationId) {
        List<LambdaAssignment> locationAssignments = lambdaAssignmentDao.getLambdaAssignments(null, locationId, false);
        if (locationAssignments == null) return List.of();

        // check if the user is assigned to the location
        Location userLocation = locationDao.getLocationByUser(user);
        if (userLocation != null && locationId == userLocation.locationId) {
            return locationAssignments;
        }

        // select lambda assignments where the use is a lambda developer and testing=true
        List<LambdaAssignment> assignments = new ArrayList<>(locationAssignments.size());
        for (LambdaAssignment assignment : locationAssignments) {
            if (!assignment.testing) continue;
            Lambda lambda = lambdaDao.getLambda(assignment.lambdaId);
            if (teamDao.checkDevTeamMember(lambda.devTeamId, user.userId)) {
                assignments.add(assignment);
            }
        }
        return assignments;
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
        deleteAssignmentsForLocation(locationId);
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

        Organization organization = organizationDao.getOrganization(user.organizationId);
        boolean organizationAdmin = organization != null && organization.adminUserId != null
            && organization.adminUserId == user.userId;
        if (!organizationAdmin && !teamDao.checkDevTeamMember(lambda.devTeamId, user.userId)) {
            throw new AccessDeniedException("Lambda assignments are not accessible");
        }

        List<Integer> lambdaAssignmentIds = lambdaAssignmentDao.getLambdaAssignmentIds(lambdaId);
        if (lambdaAssignmentIds != null) {
            for (int lambdaAssignmentId : lambdaAssignmentIds) {
                cancelLambdaAssignmentInternal(lambdaAssignmentId);
            }
        }
        logger.debug("<cancelAssignmentsForLambda()");
    }

    private void requireAssignmentLocationAccess(User user, LambdaAssignment lambdaAssignment) {
        if (lambdaAssignment == null) {
            throw new ObjectNotFoundException("Lambda assignment not found");
        }
        requireLocationAccess(user, lambdaAssignment.locationId);
    }

    private void requireLocationAccess(User user, int locationId) {
        Location userLocation = locationDao.getLocationByUser(user);
        if (userLocation == null || userLocation.locationId != locationId) {
            throw new AccessDeniedException("Location is not accessible");
        }
    }

}
