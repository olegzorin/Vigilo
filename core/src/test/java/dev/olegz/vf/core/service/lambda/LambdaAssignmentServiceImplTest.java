package dev.olegz.vf.core.service.lambda;

import java.lang.reflect.Proxy;
import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.dao.*;
import dev.olegz.vf.registry.dao.LocationDao;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.domain.account.Location;
import dev.olegz.vf.registry.domain.account.Organization;
import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaAssignment;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersion;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersionStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

class LambdaAssignmentServiceImplTest {
    @BeforeAll
    static void startPropertyStore() {
        PropertyStore.start();
    }

    @Test
    void resetProducingAssignmentMutationsAreTransactional() throws Exception {
        assertNotNull(LambdaAssignmentServiceImpl.class
            .getMethod("createLambdaAssignment", User.class, int.class, int.class, boolean.class)
            .getAnnotation(Transactional.class));
        assertNotNull(LambdaAssignmentServiceImpl.class
            .getMethod("setLambdaAssignmentEnabled", User.class, int.class, boolean.class)
            .getAnnotation(Transactional.class));
    }

    @Test
    void createLambdaAssignmentSendsResetForNewAssignment() {
        Lambda lambda = lambda(11, 31);
        LambdaAssignment[] resetAssignment = new LambdaAssignment[1];
        LambdaDao lambdaDao = proxy(LambdaDao.class, (method, args) -> switch (method) {
            case "getLambda" -> lambda;
            case "insertLambdaAssignment" -> {
                ((LambdaAssignment) args.get(0)).lambdaAssignmentId = 101;
                yield null;
            }
            default -> defaultValue(args.returnType());
        });
        LambdaClientService lambdaClientService = proxy(LambdaClientService.class, (method, args) -> {
            if (method.equals("sendResetEvent")) {
                resetAssignment[0] = (LambdaAssignment) args.get(0);
            }
            return defaultValue(args.returnType());
        });
        LambdaAssignmentService service = service(
            lambdaDao,
            proxy(LocationDao.class, (method, args) -> defaultValue(args.returnType())),
            proxy(OrganizationDao.class, (method, args) -> defaultValue(args.returnType())),
            proxy(DevTeamDao.class, (method, args) -> defaultValue(args.returnType())),
            lambdaClientService);

        int assignmentId = service.createLambdaAssignment(user(12), lambda.lambdaId, 21, false);

        assertEquals(101, assignmentId);
        assertEquals(101, resetAssignment[0].lambdaAssignmentId);
        assertEquals(21, resetAssignment[0].locationId);
    }

    @Test
    void getLambdaAssignment_allowsUserAssignedToSameLocation() {
        User user = user(12);
        LambdaAssignment assignment = assignment(101, 11, 21, false);
        Location userLocation = location(21);
        Lambda lambda = lambdaForAssignment(11, 31);
        assignment.lambda = lambda;

        LambdaAssignmentService service = service(
            proxy(LambdaDao.class, (method, args) -> switch (method) {
                case "getLambdaAssignment" -> assignment;
                case "getLambdaVersions" -> lambda.lambdaVersions;
                default -> defaultValue(args.returnType());
            }),
            proxy(LocationDao.class, (method, args) -> method.equals("getLocationByUser")
                ? userLocation : defaultValue(args.returnType())),
            proxy(DevTeamDao.class, (method, args) -> defaultValue(args.returnType())));

        LambdaAssignment result = service.getLambdaAssignment(user, assignment.lambdaAssignmentId);

        assertSame(assignment, result);
        assertSame(lambda, result.lambda);
        assertSame(lambda.getPublicLambdaVersion(), result.lambdaVersion);
    }

    @Test
    void getLambdaAssignment_allowsLambdaDeveloperForTestingAssignment() {
        User user = user(12);
        LambdaAssignment assignment = assignment(101, 11, 21, true);
        Lambda lambda = lambdaForAssignment(11, 31);
        assignment.lambda = lambda;

        LambdaAssignmentService service = service(
            proxy(LambdaDao.class, (method, args) -> switch (method) {
                case "getLambdaAssignment" -> assignment;
                case "getLambdaVersions" -> lambda.lambdaVersions;
                default -> defaultValue(args.returnType());
            }),
            proxy(LocationDao.class, (method, args) -> method.equals("getLocationByUser")
                ? location(22) : defaultValue(args.returnType())),
            proxy(DevTeamDao.class, (method, args) -> method.equals("checkDevTeamMember")
                ? true : defaultValue(args.returnType())));

        LambdaAssignment result = service.getLambdaAssignment(user, assignment.lambdaAssignmentId);

        assertSame(assignment, result);
        assertSame(lambda, result.lambda);
        assertSame(lambda.getLatestRunnableLambdaVersion(), result.lambdaVersion);
    }

    @Test
    void getLambdaAssignment_deniesDeveloperForNonTestingAssignment() {
        User user = user(12);
        LambdaAssignment assignment = assignment(101, 11, 21, false);

        LambdaAssignmentService service = service(
            proxy(LambdaDao.class, (method, args) -> switch (method) {
                case "getLambdaAssignment" -> assignment;
                case "getLambdaVersions" -> throw new AssertionError("Lambda version queried before access check");
                default -> defaultValue(args.returnType());
            }),
            proxy(LocationDao.class, (method, args) -> method.equals("getLocationByUser")
                ? location(22) : defaultValue(args.returnType())),
            proxy(DevTeamDao.class, (method, args) -> method.equals("checkDevTeamMember")
                ? true : defaultValue(args.returnType())));

        assertThrows(AccessDeniedException.class,
            () -> service.getLambdaAssignment(user, assignment.lambdaAssignmentId));
    }

    @Test
    void getLambdaAssignment_deniesNonDeveloperForTestingAssignment() {
        User user = user(12);
        LambdaAssignment assignment = assignment(101, 11, 21, true);
        assignment.lambda = lambdaForAssignment(11, 31);

        LambdaAssignmentService service = service(
            proxy(LambdaDao.class, (method, args) -> switch (method) {
                case "getLambdaAssignment" -> assignment;
                case "getLambdaVersions" -> throw new AssertionError("Lambda version queried before access check");
                default -> defaultValue(args.returnType());
            }),
            proxy(LocationDao.class, (method, args) -> method.equals("getLocationByUser")
                ? location(22) : defaultValue(args.returnType())),
            proxy(DevTeamDao.class, (method, args) -> defaultValue(args.returnType())));

        assertThrows(AccessDeniedException.class,
            () -> service.getLambdaAssignment(user, assignment.lambdaAssignmentId));
    }

    @Test
    void getLambdaAssignment_rejectsMissingAssignment() {
        LambdaAssignmentService service = service(
            proxy(LambdaDao.class, (method, args) -> defaultValue(args.returnType())),
            proxy(LocationDao.class, (method, args) -> defaultValue(args.returnType())),
            proxy(DevTeamDao.class, (method, args) -> defaultValue(args.returnType())));

        assertThrows(ObjectNotFoundException.class, () -> service.getLambdaAssignment(user(12), 101));
    }

    @Test
    void getLambdaAssignment_returnsAssignmentWithoutRunnableLambdaVersion() {
        User user = user(12);
        LambdaAssignment assignment = assignment(101, 11, 21, true);
        Lambda lambda = lambda(11, 31);
        lambda.lambdaVersions = List.of();
        assignment.lambda = lambda;

        LambdaAssignmentService service = service(
            proxy(LambdaDao.class, (method, args) -> switch (method) {
                case "getLambdaAssignment" -> assignment;
                case "getLambdaVersions" -> lambda.lambdaVersions;
                default -> defaultValue(args.returnType());
            }),
            proxy(LocationDao.class, (method, args) -> method.equals("getLocationByUser")
                ? location(21) : defaultValue(args.returnType())),
            proxy(DevTeamDao.class, (method, args) -> defaultValue(args.returnType())));

        LambdaAssignment result = service.getLambdaAssignment(user, assignment.lambdaAssignmentId);

        assertSame(assignment, result);
        assertSame(lambda, result.lambda);
        assertNull(result.lambdaVersion);
    }

    @Test
    void getLambdaAssignmentsForLambda_removesDuplicateLocationsAndPrefersUserLocationAssignment() {
        int lambdaId = 11;
        int userId = 12;
        int userLocationId = 21;

        User user = new User();
        user.userId = userId;

        Lambda lambda = lambda(lambdaId, 31);

        Location userLocation = location(userLocationId);

        LambdaAssignment userLocationAssignment = assignment(101, lambdaId, userLocationId, false);
        LambdaAssignment duplicateTestingAssignment = assignment(102, lambdaId, userLocationId, true);
        LambdaAssignment otherTestingAssignment = assignment(103, lambdaId, 22, true);

        LambdaDao lambdaDao = proxy(LambdaDao.class, (method, args) -> switch (method) {
            case "getLambda" -> lambda;
            case "getLambdaAssignments" -> args.get(1) == null
                ? List.of(duplicateTestingAssignment, otherTestingAssignment)
                : List.of(userLocationAssignment);
            default -> defaultValue(args.returnType());
        });
        LocationDao locationDao = proxy(LocationDao.class, (method, args) ->
            method.equals("getLocationByUser") ? userLocation : defaultValue(args.returnType()));
        DevTeamDao teamDao = proxy(DevTeamDao.class, (method, args) ->
            method.equals("checkDevTeamMember") ? true : defaultValue(args.returnType()));

        LambdaAssignmentService service = service(lambdaDao, locationDao, teamDao);

        List<LambdaAssignment> assignments = service.getLambdaAssignmentsForLambda(user, lambdaId);

        assertEquals(2, assignments.size());
        assertSame(userLocationAssignment, assignments.get(0));
        assertSame(otherTestingAssignment, assignments.get(1));
    }

    @Test
    void cancelLambdaAssignment_requiresUserAtAssignmentLocation() {
        User user = user(12);
        LambdaAssignment assignment = assignment(101, 11, 21, false);
        boolean[] deleted = {false};
        LambdaDao lambdaDao = proxy(LambdaDao.class, (method, args) -> switch (method) {
            case "getLambdaAssignment" -> assignment;
            case "markLambdaAssignmentDeleted" -> deleted[0] = true;
            default -> defaultValue(args.returnType());
        });

        LambdaAssignmentService deniedService = service(
            lambdaDao,
            proxy(LocationDao.class, (method, args) -> method.equals("getLocationByUser")
                ? location(22) : defaultValue(args.returnType())),
            proxy(DevTeamDao.class, (method, args) -> defaultValue(args.returnType())));

        assertThrows(AccessDeniedException.class,
            () -> deniedService.cancelLambdaAssignment(user, assignment.lambdaAssignmentId));
        assertFalse(deleted[0]);

        LambdaAssignmentService allowedService = service(
            lambdaDao,
            proxy(LocationDao.class, (method, args) -> method.equals("getLocationByUser")
                ? location(21) : defaultValue(args.returnType())),
            proxy(DevTeamDao.class, (method, args) -> defaultValue(args.returnType())));

        allowedService.cancelLambdaAssignment(user, assignment.lambdaAssignmentId);
        assertTrue(deleted[0]);
    }

    @Test
    void cancelLambdaAssignment_enqueuesRuntimeDataCleanupAfterMarkingAssignmentDeleted() {
        User user = user(12);
        LambdaAssignment assignment = assignment(101, 11, 21, false);
        List<String> operations = new java.util.ArrayList<>();
        LambdaDao lambdaDao = proxy(LambdaDao.class, (method, args) -> switch (method) {
            case "getLambdaAssignment" -> assignment;
            case "markLambdaAssignmentDeleted" -> {
                operations.add("assignment");
                yield true;
            }
            default -> defaultValue(args.returnType());
        });
        LambdaAssignmentCleanupService cleanupService = proxy(LambdaAssignmentCleanupService.class, (method, args) -> {
            if (method.equals("enqueueLambdaAssignmentCleanup")) operations.add("cleanup");
            return defaultValue(args.returnType());
        });
        LambdaAssignmentService service = service(
            lambdaDao,
            cleanupService,
            proxy(LocationDao.class, (method, args) -> method.equals("getLocationByUser")
                ? location(21) : defaultValue(args.returnType())),
            proxy(OrganizationDao.class, (method, args) -> defaultValue(args.returnType())),
            proxy(DevTeamDao.class, (method, args) -> defaultValue(args.returnType())),
            proxy(LambdaClientService.class, (method, args) -> defaultValue(args.returnType())));

        service.cancelLambdaAssignment(user, assignment.lambdaAssignmentId);

        assertEquals(List.of("assignment", "cleanup"), operations);
    }

    @Test
    void cancelLambdaAssignment_doesNotEnqueueCleanupWhenAssignmentWasAlreadyDeleted() {
        User user = user(12);
        LambdaAssignment assignment = assignment(101, 11, 21, false);
        boolean[] cleanupEnqueued = {false};
        LambdaDao lambdaDao = proxy(LambdaDao.class, (method, args) -> switch (method) {
            case "getLambdaAssignment" -> assignment;
            case "markLambdaAssignmentDeleted" -> false;
            default -> defaultValue(args.returnType());
        });
        LambdaAssignmentCleanupService cleanupService = proxy(LambdaAssignmentCleanupService.class, (method, args) -> {
            if (method.equals("enqueueLambdaAssignmentCleanup")) cleanupEnqueued[0] = true;
            return defaultValue(args.returnType());
        });
        LambdaAssignmentService service = service(
            lambdaDao,
            cleanupService,
            proxy(LocationDao.class, (method, args) -> method.equals("getLocationByUser")
                ? location(21) : defaultValue(args.returnType())),
            proxy(OrganizationDao.class, (method, args) -> defaultValue(args.returnType())),
            proxy(DevTeamDao.class, (method, args) -> defaultValue(args.returnType())),
            proxy(LambdaClientService.class, (method, args) -> defaultValue(args.returnType())));

        service.cancelLambdaAssignment(user, assignment.lambdaAssignmentId);

        assertFalse(cleanupEnqueued[0]);
    }

    @Test
    void setLambdaAssignmentEnabled_requiresExistingAssignmentAtUserLocation() {
        LambdaAssignmentService missingAssignmentService = service(
            proxy(LambdaDao.class, (method, args) -> {
                if (method.equals("updateLambdaAssignment")) throw new AssertionError("Missing assignment updated");
                return defaultValue(args.returnType());
            }),
            proxy(LocationDao.class, (method, args) -> defaultValue(args.returnType())),
            proxy(DevTeamDao.class, (method, args) -> defaultValue(args.returnType())));

        assertThrows(ObjectNotFoundException.class,
            () -> missingAssignmentService.setLambdaAssignmentEnabled(user(12), 101, false));

        User user = user(12);
        LambdaAssignment assignment = assignment(101, 11, 21, true);
        boolean[] updated = {false};
        LambdaDao lambdaDao = proxy(LambdaDao.class, (method, args) -> switch (method) {
            case "getLambdaAssignment" -> assignment;
            case "updateLambdaAssignment" -> {
                updated[0] = true;
                yield true;
            }
            default -> defaultValue(args.returnType());
        });
        LambdaAssignmentService deniedService = service(
            lambdaDao,
            proxy(LocationDao.class, (method, args) -> method.equals("getLocationByUser")
                ? location(22) : defaultValue(args.returnType())),
            proxy(DevTeamDao.class, (method, args) -> defaultValue(args.returnType())));

        assertThrows(AccessDeniedException.class,
            () -> deniedService.setLambdaAssignmentEnabled(user, assignment.lambdaAssignmentId, false));
        assertFalse(updated[0]);

        LambdaAssignmentService allowedService = service(
            lambdaDao,
            proxy(LocationDao.class, (method, args) -> method.equals("getLocationByUser")
                ? location(21) : defaultValue(args.returnType())),
            proxy(DevTeamDao.class, (method, args) -> defaultValue(args.returnType())));

        allowedService.setLambdaAssignmentEnabled(user, assignment.lambdaAssignmentId, false);

        assertTrue(updated[0]);
    }

    @Test
    void setLambdaAssignmentEnabledSendsResetOnlyForInactiveToActiveTransition() {
        User user = user(12);
        LambdaAssignment assignment = assignment(101, 11, 21, false);
        assignment.endDate = new Datetime(System.currentTimeMillis() - 60_000L);
        int[] resetCount = {0};
        LambdaDao lambdaDao = proxy(LambdaDao.class, (method, args) -> switch (method) {
            case "getLambdaAssignment" -> assignment;
            case "updateLambdaAssignment" -> true;
            default -> defaultValue(args.returnType());
        });
        LambdaClientService lambdaClientService = proxy(LambdaClientService.class, (method, args) -> {
            if (method.equals("sendResetEvent")) resetCount[0]++;
            return defaultValue(args.returnType());
        });
        LambdaAssignmentService service = service(
            lambdaDao,
            proxy(LocationDao.class, (method, args) -> method.equals("getLocationByUser")
                ? location(21) : defaultValue(args.returnType())),
            proxy(OrganizationDao.class, (method, args) -> defaultValue(args.returnType())),
            proxy(DevTeamDao.class, (method, args) -> defaultValue(args.returnType())),
            lambdaClientService);

        service.setLambdaAssignmentEnabled(user, assignment.lambdaAssignmentId, true);
        assertNull(assignment.endDate);
        service.setLambdaAssignmentEnabled(user, assignment.lambdaAssignmentId, true);
        service.setLambdaAssignmentEnabled(user, assignment.lambdaAssignmentId, false);

        assertEquals(1, resetCount[0]);
    }

    @Test
    void setLambdaAssignmentEnabledSendsResetWhenTestingAssignmentGetsFutureEndDate() {
        User user = user(12);
        LambdaAssignment assignment = assignment(101, 11, 21, true);
        assignment.endDate = new Datetime(System.currentTimeMillis() - 60_000L);
        int[] resetCount = {0};
        LambdaDao lambdaDao = proxy(LambdaDao.class, (method, args) -> switch (method) {
            case "getLambdaAssignment" -> assignment;
            case "updateLambdaAssignment" -> true;
            default -> defaultValue(args.returnType());
        });
        LambdaClientService lambdaClientService = proxy(LambdaClientService.class, (method, args) -> {
            if (method.equals("sendResetEvent")) resetCount[0]++;
            return defaultValue(args.returnType());
        });
        LambdaAssignmentService service = service(
            lambdaDao,
            proxy(LocationDao.class, (method, args) -> method.equals("getLocationByUser")
                ? location(21) : defaultValue(args.returnType())),
            proxy(OrganizationDao.class, (method, args) -> defaultValue(args.returnType())),
            proxy(DevTeamDao.class, (method, args) -> defaultValue(args.returnType())),
            lambdaClientService);

        service.setLambdaAssignmentEnabled(user, assignment.lambdaAssignmentId, true);

        assertTrue(assignment.endDate.getTime() > System.currentTimeMillis());
        assertEquals(1, resetCount[0]);
    }

    @Test
    void setLambdaAssignmentEnabled_throwsWhenAssignmentIsDeletedBeforeUpdate() {
        User user = user(12);
        LambdaAssignment assignment = assignment(101, 11, 21, false);
        LambdaDao lambdaDao = proxy(LambdaDao.class, (method, args) -> switch (method) {
            case "getLambdaAssignment" -> assignment;
            case "updateLambdaAssignment" -> false;
            default -> defaultValue(args.returnType());
        });
        LambdaAssignmentService service = service(
            lambdaDao,
            proxy(LocationDao.class, (method, args) -> method.equals("getLocationByUser")
                ? location(21) : defaultValue(args.returnType())),
            proxy(DevTeamDao.class, (method, args) -> defaultValue(args.returnType())));

        assertThrows(ObjectNotFoundException.class,
            () -> service.setLambdaAssignmentEnabled(user, assignment.lambdaAssignmentId, true));
    }

    @Test
    void cancelAssignmentsForLocation_checksAccessBeforeQueryingAssignments() {
        LambdaAssignmentService service = service(
            proxy(LambdaDao.class, (method, args) -> {
                if (method.equals("getLambdaAssignments")) throw new AssertionError("Assignments queried before access check");
                return defaultValue(args.returnType());
            }),
            proxy(LocationDao.class, (method, args) -> method.equals("getLocationByUser")
                ? location(22) : defaultValue(args.returnType())),
            proxy(DevTeamDao.class, (method, args) -> defaultValue(args.returnType())));

        assertThrows(AccessDeniedException.class,
            () -> service.cancelAssignmentsForLocation(user(12), 21));
    }

    @Test
    void deleteAssignmentsForLocation_deletesEveryAssignment() {
        List<Integer> deletedIds = new java.util.ArrayList<>();
        LambdaAssignmentService service = service(
            proxy(LambdaDao.class, (method, args) -> switch (method) {
                case "getLambdaAssignmentIdsForLocation" -> List.of(101, 102);
                case "markLambdaAssignmentDeleted" -> {
                    deletedIds.add((Integer) args.get(0));
                    yield true;
                }
                default -> defaultValue(args.returnType());
            }),
            proxy(LocationDao.class, (method, args) -> defaultValue(args.returnType())),
            proxy(DevTeamDao.class, (method, args) -> defaultValue(args.returnType())));

        service.deleteAssignmentsForLocation(21);

        assertEquals(List.of(101, 102), deletedIds);
    }

    @Test
    void cancelAssignmentsForLambda_allowsDeveloperAndRejectsOtherUsers() {
        User user = user(12);
        user.organizationId = 41;
        Lambda lambda = lambda(11, 31);
        boolean[] deleted = {false};
        LambdaDao lambdaDao = proxy(LambdaDao.class, (method, args) -> switch (method) {
            case "getLambda" -> lambda;
            case "getLambdaAssignmentIds" -> List.of(101);
            case "markLambdaAssignmentDeleted" -> deleted[0] = true;
            default -> defaultValue(args.returnType());
        });

        LambdaAssignmentService deniedService = service(
            lambdaDao,
            proxy(LocationDao.class, (method, args) -> defaultValue(args.returnType())),
            proxy(OrganizationDao.class, (method, args) -> defaultValue(args.returnType())),
            proxy(DevTeamDao.class, (method, args) -> defaultValue(args.returnType())));

        assertThrows(AccessDeniedException.class,
            () -> deniedService.cancelAssignmentsForLambda(user, lambda.lambdaId));
        assertFalse(deleted[0]);

        LambdaAssignmentService developerService = service(
            lambdaDao,
            proxy(LocationDao.class, (method, args) -> defaultValue(args.returnType())),
            proxy(OrganizationDao.class, (method, args) -> defaultValue(args.returnType())),
            proxy(DevTeamDao.class, (method, args) -> method.equals("checkDevTeamMember")
                ? true : defaultValue(args.returnType())));

        developerService.cancelAssignmentsForLambda(user, lambda.lambdaId);
        assertTrue(deleted[0]);
    }

    @Test
    void cancelAssignmentsForLambda_allowsOrganizationAdmin() {
        User user = user(12);
        user.organizationId = 41;
        Lambda lambda = lambda(11, 31);
        Organization organization = new Organization();
        organization.organizationId = user.organizationId;
        organization.adminUserId = user.userId;
        boolean[] deleted = {false};

        LambdaAssignmentService service = service(
            proxy(LambdaDao.class, (method, args) -> switch (method) {
                case "getLambda" -> lambda;
                case "getLambdaAssignmentIds" -> List.of(101);
                case "markLambdaAssignmentDeleted" -> deleted[0] = true;
                default -> defaultValue(args.returnType());
            }),
            proxy(LocationDao.class, (method, args) -> defaultValue(args.returnType())),
            proxy(OrganizationDao.class, (method, args) -> method.equals("getOrganization")
                ? organization : defaultValue(args.returnType())),
            proxy(DevTeamDao.class, (method, args) -> defaultValue(args.returnType())));

        service.cancelAssignmentsForLambda(user, lambda.lambdaId);

        assertTrue(deleted[0]);
    }

    private static LambdaAssignment assignment(int assignmentId, int lambdaId, int locationId, boolean testing) {
        LambdaAssignment assignment = new LambdaAssignment(lambdaId, locationId, testing);
        assignment.lambdaAssignmentId = assignmentId;
        return assignment;
    }

    private static User user(int userId) {
        User user = new User();
        user.userId = userId;
        return user;
    }

    private static Location location(int locationId) {
        Location location = new Location();
        location.locationId = locationId;
        return location;
    }

    private static Lambda lambda(int lambdaId, int devTeamId) {
        Lambda lambda = new Lambda();
        lambda.lambdaId = lambdaId;
        lambda.devTeamId = devTeamId;
        return lambda;
    }

    private static Lambda lambdaForAssignment(int lambdaId, int devTeamId) {
        Lambda lambda = lambda(lambdaId, devTeamId);
        lambda.lambdaVersions = List.of(
            lambdaVersion(42, lambdaId, LambdaVersionStatus.TESTING),
            lambdaVersion(41, lambdaId, LambdaVersionStatus.PRODUCTION));
        return lambda;
    }

    private static LambdaVersion lambdaVersion(int lambdaVersionId, int lambdaId, LambdaVersionStatus status) {
        LambdaVersion lambdaVersion = new LambdaVersion();
        lambdaVersion.lambdaVersionId = lambdaVersionId;
        lambdaVersion.lambdaId = lambdaId;
        lambdaVersion.status = status;
        return lambdaVersion;
    }

    private static LambdaAssignmentService service(LambdaDao lambdaDao, LocationDao locationDao, DevTeamDao teamDao) {
        return service(
            lambdaDao,
            locationDao,
            proxy(OrganizationDao.class, (method, args) -> defaultValue(args.returnType())),
            teamDao);
    }

    private static LambdaAssignmentService service(LambdaDao lambdaDao, LocationDao locationDao,
        OrganizationDao organizationDao, DevTeamDao teamDao) {
        return service(
            lambdaDao,
            locationDao,
            organizationDao,
            teamDao,
            proxy(LambdaClientService.class, (method, args) -> defaultValue(args.returnType())));
    }

    private static LambdaAssignmentService service(LambdaDao lambdaDao, LocationDao locationDao,
        OrganizationDao organizationDao, DevTeamDao teamDao, LambdaClientService lambdaClientService) {
        return service(
            lambdaDao,
            proxy(LambdaAssignmentCleanupService.class, (method, args) -> defaultValue(args.returnType())),
            locationDao,
            organizationDao,
            teamDao,
            lambdaClientService);
    }

    private static LambdaAssignmentService service(LambdaDao lambdaDao, LambdaAssignmentCleanupService cleanupService,
        LocationDao locationDao, OrganizationDao organizationDao,
        DevTeamDao teamDao, LambdaClientService lambdaClientService) {
        return new LambdaAssignmentServiceImpl(
            lambdaDao,
            (LambdaAssignmentDao) lambdaDao,
            cleanupService,
            locationDao,
            organizationDao,
            teamDao,
            lambdaClientService);
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        Class<?>[] interfaces = type == LambdaDao.class ? new Class<?>[]{LambdaDao.class, LambdaAssignmentDao.class} :
            new Class<?>[]{type};
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), interfaces,
            (proxy, method, args) -> invocation.invoke(method.getName(), new Arguments(args, method.getReturnType()))));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Arguments args);
    }

    private record Arguments(Object[] values, Class<?> returnType) {
        private Object get(int index) {
            return values[index];
        }
    }
}
