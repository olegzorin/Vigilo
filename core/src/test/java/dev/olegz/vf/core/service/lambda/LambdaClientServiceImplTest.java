package dev.olegz.vf.core.service.lambda;

import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.common.exception.InvalidJwtException;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.dao.*;
import dev.olegz.vf.registry.dao.DeviceDao;
import dev.olegz.vf.registry.dao.LocationDao;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.domain.account.Location;
import dev.olegz.vf.registry.domain.account.LocationCurrentState;
import dev.olegz.vf.registry.domain.account.Organization;
import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaAssignment;
import dev.olegz.vf.core.domain.lambdarun.*;
import dev.olegz.vf.registry.domain.device.DeviceCurrentState;
import dev.olegz.vf.core.event.ResetEvent;
import dev.olegz.vf.core.event.ScheduledEvent;
import dev.olegz.vf.core.event.TriggerEvent;
import dev.olegz.vf.registry.service.encryption.JwtService;
import dev.olegz.vf.messaging.ConfirmingKeyedMessageProducer;
import dev.olegz.vf.messaging.MessagingException;
import dev.olegz.vf.messaging.Topics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LambdaClientServiceImplTest {

    @BeforeAll
    static void oneTimeSetUp() {
        PropertyStore.start();
    }

    @Test
    void rejectsNullLambdaKeyAsInvalidJwt() {
        LambdaClientService service = service(
            proxy(LambdaVariableDao.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            11);

        assertThrows(InvalidJwtException.class, () -> service.parseLambdaKey(null));
    }

    @Test
    void persistsAssignmentResetForConfirmedPublication() {
        ResetEvent[] persisted = new ResetEvent[1];
        LambdaResetOutboxService resetOutboxService = proxy(LambdaResetOutboxService.class, (_, method, args) -> {
            if (method.getName().equals("enqueue")) {
                persisted[0] = (ResetEvent) args[0];
            }
            return defaultValue(method.getReturnType());
        });
        LambdaClientService service = new LambdaClientServiceImpl(
            proxy(LambdaRunDao.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            proxy(LambdaVariableDao.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            proxy(LocationDao.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            proxy(DeviceDao.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            proxy(OrganizationDao.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            proxy(JwtService.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            null,
            resetOutboxService,
            NOOP_PRODUCER);
        LambdaAssignment assignment = new LambdaAssignment(11, 21, false);
        assignment.lambdaAssignmentId = 101;

        service.sendResetEvent(assignment);

        ResetEvent event = persisted[0];
        assertEquals(21, event.locationId);
        assertEquals(101, event.lambdaAssignmentId);
        assertNotNull(event.eventId);
        assertTrue(event.time > 0);
        assertEquals(event.time, event.variableGeneration);
    }

    @Test
    void confirmsTriggerPublicationAndPropagatesBrokerFailure() {
        RecordingProducer producer = new RecordingProducer();
        LambdaClientService service = service(producer);
        TriggerEvent event = new TriggerEvent();
        event.locationId = 21;

        service.publishTriggerEvent(event);

        assertEquals(Topics.LAMBDA_INPUT, producer.topic);
        assertEquals(21, producer.key);
        assertNotNull(event.eventId);
        assertTrue(event.time > 0L);

        String eventId = event.eventId;
        producer.failure = new MessagingException("broker unavailable");
        assertThrows(MessagingException.class, () -> service.publishTriggerEvent(event));
        assertEquals(eventId, event.eventId);
    }

    @Test
    void dispatchesAssignmentScheduledEventWithFiredScheduleIds() {
        ScheduledEvent[] dispatched = new ScheduledEvent[1];
        LambdaClientServiceImpl service = new LambdaClientServiceImpl(
            proxy(LambdaRunDao.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            proxy(LambdaVariableDao.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            proxy(LocationDao.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            proxy(DeviceDao.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            proxy(OrganizationDao.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            proxy(JwtService.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            proxy(ConfirmingKeyedMessageProducer.class, (proxy, method, args) -> defaultValue(method.getReturnType())));
        ScheduledLambdaAssignment assignment = new ScheduledLambdaAssignment();
        assignment.lambdaAssignmentId = 101;
        assignment.locationId = 21;
        assignment.scheduleLastDate = new Timestamp(100_000L);
        assignment.scheduleNextDate = new Timestamp(120_000L);

        service.dispatchScheduledEvent(
            assignment,
            List.of("morning", "medication"),
            123_000L,
            event -> dispatched[0] = event);

        ScheduledEvent event = dispatched[0];
        assertEquals(21, event.locationId);
        assertEquals(101, event.lambdaAssignmentId);
        assertEquals(123_000L, event.time);
        assertEquals(List.of("morning", "medication"), event.scheduleIds);
        assertNotNull(event.eventId);

        service.dispatchScheduledEvent(
            assignment,
            List.of("morning", "medication"),
            124_000L,
            replay -> assertEquals(event.eventId, replay.eventId));
    }

    @Test
    void expiredRunRecoveryIdempotentlyEnqueuesCompletionIntent() {
        LambdaRun expired = new LambdaRun();
        expired.lambdaAssignmentId = 101;
        expired.invocationLane = InvocationLane.DEFAULT;
        expired.runId = 123_000L;
        LambdaRun expiredAsync = new LambdaRun();
        expiredAsync.lambdaAssignmentId = 102;
        expiredAsync.invocationLane = InvocationLane.ASYNC;
        expiredAsync.runId = 124_000L;
        List<LambdaRunContext> enqueued = new ArrayList<>();
        LambdaRunDao runDao = proxy(LambdaRunDao.class, (_, method, _) -> switch (method.getName()) {
            case "getLambdaRunsRequiringCompletion" -> List.of(expired);
            case "getAsyncLambdaRunsRequiringCompletion" -> List.of(expiredAsync);
            default -> defaultValue(method.getReturnType());
        });
        LambdaRunCompletionOutboxService outboxService = proxy(
            LambdaRunCompletionOutboxService.class,
            (_, method, args) -> {
                if (method.getName().equals("enqueue")) {
                    enqueued.add((LambdaRunContext) args[0]);
                    return null;
                }
                return defaultValue(method.getReturnType());
            });
        LambdaClientServiceImpl service = new LambdaClientServiceImpl(
            runDao,
            proxy(LambdaVariableDao.class, (_, method, _) -> defaultValue(method.getReturnType())),
            proxy(LocationDao.class, (_, method, _) -> defaultValue(method.getReturnType())),
            proxy(DeviceDao.class, (_, method, _) -> defaultValue(method.getReturnType())),
            proxy(OrganizationDao.class, (_, method, _) -> defaultValue(method.getReturnType())),
            proxy(JwtService.class, (_, method, _) -> defaultValue(method.getReturnType())),
            outboxService,
            null,
            NOOP_PRODUCER);

        service.dispatchExpiredLambdaRunCompletions();
        service.dispatchExpiredLambdaRunCompletions();

        assertEquals(4, enqueued.size());
        assertEquals(101, enqueued.get(0).lambdaAssignmentId);
        assertEquals(123_000L, enqueued.get(0).requestId);
        assertEquals(InvocationLane.DEFAULT, enqueued.get(0).lane);
        assertEquals(102, enqueued.get(1).lambdaAssignmentId);
        assertEquals(124_000L, enqueued.get(1).requestId);
        assertEquals(InvocationLane.ASYNC, enqueued.get(1).lane);
    }

    @Test
    void failedScheduledEventPublicationDoesNotAdvanceScheduleCursor() {
        ScheduledLambdaAssignment assignment = new ScheduledLambdaAssignment();
        assignment.lambdaAssignmentId = 101;
        assignment.lambdaVersionId = 31;
        assignment.locationId = 21;
        assignment.timezone = "UTC";
        assignment.scheduleLastDate = new Timestamp(System.currentTimeMillis() - 3_000L);
        assignment.scheduleNextDate = new Timestamp(System.currentTimeMillis() - 2_000L);
        assignment.schedule = "{\"morning\":\"* * * * * ?\"}";
        AtomicInteger cursorUpdates = new AtomicInteger();
        LambdaRunDao runDao = proxy(LambdaRunDao.class, (proxy, method, args) -> {
            if (method.getName().equals("getScheduledLambdaAssignments")) return List.of(assignment);
            if (method.getName().equals("updateScheduleDatesIfUnchanged")) {
                cursorUpdates.incrementAndGet();
                return true;
            }
            return defaultValue(method.getReturnType());
        });
        LambdaClientServiceImpl service = new LambdaClientServiceImpl(
            runDao,
            proxy(LambdaVariableDao.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            proxy(LocationDao.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            proxy(DeviceDao.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            proxy(OrganizationDao.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            proxy(JwtService.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            proxy(ConfirmingKeyedMessageProducer.class, (proxy, method, args) -> defaultValue(method.getReturnType())));

        service.triggerScheduledLambdas(event -> {
            throw new RuntimeException("broker unavailable");
        });

        assertEquals(0, cursorUpdates.get());
    }

    @Test
    void firesFirstScheduledAssignmentOfEachLambdaVersionIndependently() throws Exception {
        CountDownLatch firstVersionAssignmentsStarted = new CountDownLatch(2);
        List<String> fired = java.util.Collections.synchronizedList(new ArrayList<>());
        Map<Integer, List<Runnable>> triggersByLambdaVersion = new LinkedHashMap<>();
        triggersByLambdaVersion.put(11, List.of(
            () -> awaitOtherLambdaVersion(firstVersionAssignmentsStarted, fired, "version1-first"),
            () -> fired.add("version1-second")));
        triggersByLambdaVersion.put(22, List.of(
            () -> awaitOtherLambdaVersion(firstVersionAssignmentsStarted, fired, "version2-first"),
            () -> fired.add("version2-second")));

        LambdaClientServiceImpl.fireIndependentlyByLambdaVersion(triggersByLambdaVersion, 0L);

        assertEquals(4, fired.size());
        assertTrue(fired.indexOf("version1-first") < fired.indexOf("version1-second"));
        assertTrue(fired.indexOf("version2-first") < fired.indexOf("version2-second"));
    }

    @Test
    void shrinksIntervalForManyScheduledAssignmentsOfOneLambdaVersion() {
        assertEquals(1_000L, LambdaClientServiceImpl.timeUntilNextFire(11_000L, 10_000L, 1));
        assertEquals(500L, LambdaClientServiceImpl.timeUntilNextFire(11_000L, 10_000L, 2));
        assertEquals(250L, LambdaClientServiceImpl.timeUntilNextFire(11_000L, 10_000L, 4));
        assertEquals(0L, LambdaClientServiceImpl.timeUntilNextFire(11_000L, 11_001L, 4));
    }

    @Test
    void generatesLambdaApiKeyForLocationOwnerAndOrganizationAdmin() {
        LambdaRuntimeAssignment assignment = runtimeAssignment();
        User locationOwner = user(7, 41);
        User organizationAdmin = user(8, 41);
        Organization organization = organization(41, organizationAdmin.userId);
        LambdaClientService service = service(assignment, locationOwner, organization, "lambda-key");

        LambdaKeyInput ownerKey = service.generateLambdaApiKey(locationOwner, assignment.lambdaAssignmentId);
        LambdaKeyInput adminKey = service.generateLambdaApiKey(organizationAdmin, assignment.lambdaAssignmentId);

        assertEquals("lambda-key", ownerKey.key);
        assertEquals("lambda-key", adminKey.key);
        assertTrue(ownerKey.expiry > 0);
        assertTrue(adminKey.expiry > 0);
    }

    @Test
    void rejectsLambdaApiKeyForUnrelatedUserAndInactiveAssignment() {
        LambdaRuntimeAssignment assignment = runtimeAssignment();
        User locationOwner = user(7, 41);
        Organization organization = organization(41, 8);
        LambdaClientService service = service(assignment, locationOwner, organization, "lambda-key");

        assertThrows(AccessDeniedException.class,
            () -> service.generateLambdaApiKey(user(9, 41), assignment.lambdaAssignmentId));
        assertThrows(AccessDeniedException.class,
            () -> service.generateLambdaApiKey(user(8, 42), assignment.lambdaAssignmentId));

        LambdaClientService inactiveService = service(null, locationOwner, organization, "lambda-key");
        assertThrows(ObjectNotFoundException.class,
            () -> inactiveService.generateLambdaApiKey(locationOwner, assignment.lambdaAssignmentId));

        LambdaClientService failedService = service(assignment, locationOwner, organization, null);
        assertThrows(ApplicationFailureException.class,
            () -> failedService.generateLambdaApiKey(locationOwner, assignment.lambdaAssignmentId));
    }

    @Test
    void updatesAuthorizedLocationAndDeviceCurrentStates() {
        LocationCurrentState[] savedLocation = new LocationCurrentState[1];
        DeviceCurrentState[] savedDevice = new DeviceCurrentState[1];
        LambdaClientService service = service(
            (proxy, method, args) -> method.getName().equals("saveLocationCurrentState")
                ? save(savedLocation, (LocationCurrentState) args[0])
                : defaultValue(method.getReturnType()),
            (proxy, method, args) -> method.getName().equals("saveDeviceCurrentState")
                ? save(savedDevice, (DeviceCurrentState) args[0])
                : defaultValue(method.getReturnType()));

        LocationCurrentState locationState =
            service.updateLocationCurrentState("lambda-key", 11, "HOME");
        Datetime measuredAt = Datetime.nowMinusDays(1);
        DeviceCurrentState deviceState = service.updateDeviceCurrentState(
            "lambda-key",
            "device-1",
            Map.of("status", "online"),
            measuredAt);

        assertEquals(locationState, savedLocation[0]);
        assertEquals(11, locationState.locationId);
        assertEquals("HOME", locationState.state);
        assertNotNull(locationState.stateDate);
        assertEquals(deviceState, savedDevice[0]);
        assertEquals("device-1", deviceState.deviceUuid);
        assertEquals(Map.of("status", "online"), deviceState.state);
        assertEquals(measuredAt, deviceState.measuredAt);
        assertNotNull(deviceState.receivedAt);
    }

    @Test
    void rejectsCurrentStateUpdatesOutsideLambdaLocation() {
        LambdaClientService service = service(
            (proxy, method, args) -> {
                if (method.getName().equals("saveLocationCurrentState")) {
                    throw new AssertionError("Unauthorized location state was saved");
                }
                return defaultValue(method.getReturnType());
            },
            (proxy, method, args) -> {
                if (method.getName().equals("saveDeviceCurrentState")) {
                    throw new AssertionError("Unauthorized device state was saved");
                }
                return defaultValue(method.getReturnType());
            });

        assertThrows(AccessDeniedException.class,
            () -> service.updateLocationCurrentState("lambda-key", 12, "AWAY"));
        assertThrows(AccessDeniedException.class,
            () -> service.updateDeviceCurrentState(
                "lambda-key",
                "device-2",
                Map.of(),
                Datetime.now()));
    }

    @Test
    void accessesAssignmentAndLocationVariablesFromLambdaKeyClaims() {
        List<String> writes = new java.util.ArrayList<>();
        LambdaVariableDao variableDao = proxy(LambdaVariableDao.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getLambdaAssignmentVariable":
                    assertEquals(1, args[0]);
                    assertEquals(77L, args[1]);
                    assertEquals("private", args[2]);
                    return new byte[]{1, 2};
                case "getLambdaLocationVariable":
                    assertEquals(11, args[0]);
                    assertEquals("shared", args[1]);
                    return new byte[]{3, 4};
                case "putLambdaAssignmentVariable":
                    assertEquals(1, args[0]);
                    assertEquals(77L, args[1]);
                    assertEquals("private", args[2]);
                    writes.add("put-private");
                    return null;
                case "putLambdaLocationVariable":
                    assertEquals(11, args[0]);
                    assertEquals("shared", args[1]);
                    writes.add("put-shared");
                    return null;
                case "deleteLambdaAssignmentVariable":
                    assertEquals(1, args[0]);
                    assertEquals(77L, args[1]);
                    assertEquals("private", args[2]);
                    writes.add("delete-private");
                    return null;
                case "deleteLambdaLocationVariable":
                    assertEquals(11, args[0]);
                    assertEquals("shared", args[1]);
                    writes.add("delete-shared");
                    return null;
                default:
                    return defaultValue(method.getReturnType());
            }
        });
        LambdaClientService service = service(variableDao, 11);

        assertArrayEquals(new byte[]{1, 2}, service.getVariable("lambda-key", "private", false));
        assertArrayEquals(new byte[]{3, 4}, service.getVariable("lambda-key", "shared", true));
        service.putVariable("lambda-key", "private", false, new byte[]{5});
        service.putVariable("lambda-key", "shared", true, new byte[]{6});
        service.deleteVariable("lambda-key", "private", false);
        service.deleteVariable("lambda-key", "shared", true);

        assertEquals(
            List.of("put-private", "put-shared", "delete-private", "delete-shared"),
            writes);
    }

    @Test
    void rejectsInvalidVariableNamesAndSharedAccessWithoutLocation() {
        LambdaClientService service = service(
            proxy(LambdaVariableDao.class, (proxy, method, args) -> {
                throw new AssertionError("Rejected variable access reached the DAO");
            }),
            0);

        assertThrows(dev.olegz.vf.common.exception.WrongParameterValueException.class,
            () -> service.getVariable("lambda-key", " ", false));
        assertThrows(dev.olegz.vf.common.exception.WrongParameterValueException.class,
            () -> service.getVariable("lambda-key", "x".repeat(151), false));
        assertThrows(AccessDeniedException.class,
            () -> service.getVariable("lambda-key", "shared", true));
    }

    private static LambdaClientService service(LambdaVariableDao lambdaVariableDao, int locationId) {
        JwtService jwtService =
            proxy(JwtService.class, (proxy, method, args) -> {
                if (method.getName().equals("verifyJwt")) {
                    LambdaKeyJwtClaims claims = new LambdaKeyJwtClaims();
                    claims.aid = 1;
                    claims.bid = 2;
                    claims.lid = locationId;
                    claims.vgen = 77;
                    return claims;
                }
                return defaultValue(method.getReturnType());
            });
        return new LambdaClientServiceImpl(
            proxy(LambdaRunDao.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            lambdaVariableDao,
            proxy(LocationDao.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            proxy(DeviceDao.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            proxy(OrganizationDao.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            jwtService,
            NOOP_PRODUCER);
    }

    private static LambdaClientService service(ConfirmingKeyedMessageProducer producer) {
        return new LambdaClientServiceImpl(
            proxy(LambdaRunDao.class, (_, method, _) -> defaultValue(method.getReturnType())),
            proxy(LambdaVariableDao.class, (_, method, _) -> defaultValue(method.getReturnType())),
            proxy(LocationDao.class, (_, method, _) -> defaultValue(method.getReturnType())),
            proxy(DeviceDao.class, (_, method, _) -> defaultValue(method.getReturnType())),
            proxy(OrganizationDao.class, (_, method, _) -> defaultValue(method.getReturnType())),
            proxy(JwtService.class, (_, method, _) -> defaultValue(method.getReturnType())),
            producer);
    }

    private static LambdaClientService service(
        java.lang.reflect.InvocationHandler locationHandler,
        java.lang.reflect.InvocationHandler deviceHandler)
    {
        LambdaRunDao lambdaRunDao = proxy(LambdaRunDao.class, (proxy, method, args) ->
            method.getName().equals("checkLambdaDeviceAccess") &&
                "device-1".equals(args[0]) &&
                Integer.valueOf(11).equals(args[1]));
        JwtService jwtService =
            proxy(JwtService.class, (proxy, method, args) -> {
                if (method.getName().equals("verifyJwt")) {
                    LambdaKeyJwtClaims claims = new LambdaKeyJwtClaims();
                    claims.aid = 1;
                    claims.bid = 2;
                    claims.lid = 11;
                    return claims;
                }
                return defaultValue(method.getReturnType());
            });

        return new LambdaClientServiceImpl(
            lambdaRunDao,
            proxy(LambdaVariableDao.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            proxy(LocationDao.class, locationHandler),
            proxy(DeviceDao.class, deviceHandler),
            proxy(OrganizationDao.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            jwtService,
            NOOP_PRODUCER);
    }

    private static LambdaClientService service(
        LambdaRuntimeAssignment assignment,
        User locationOwner,
        Organization organization,
        String generatedKey)
    {
        return new LambdaClientServiceImpl(
            proxy(LambdaRunDao.class, (proxy, method, args) ->
                method.getName().equals("getActiveLambdaRuntimeAssignment") ? assignment : defaultValue(method.getReturnType())),
            proxy(LambdaVariableDao.class, (proxy, method, args) ->
                method.getName().equals("getLambdaAssignmentVariableGeneration")
                    ? 77L
                    : defaultValue(method.getReturnType())),
            proxy(LocationDao.class, (proxy, method, args) -> {
                if (!method.getName().equals("getLocationByUser") || args[0] != locationOwner) {
                    return defaultValue(method.getReturnType());
                }
                Location location = new Location();
                location.locationId = assignment.locationId;
                return location;
            }),
            proxy(DeviceDao.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            proxy(OrganizationDao.class, (proxy, method, args) ->
                method.getName().equals("getOrganization") ? organization : defaultValue(method.getReturnType())),
            proxy(JwtService.class, (proxy, method, args) -> {
                if (!method.getName().equals("createJwt")) {
                    return defaultValue(method.getReturnType());
                }
                assertEquals(77L, ((LambdaKeyJwtClaims) args[0]).vgen);
                return generatedKey;
            }),
            NOOP_PRODUCER);
    }

    private static LambdaRuntimeAssignment runtimeAssignment() {
        LambdaRuntimeAssignment assignment = new LambdaRuntimeAssignment();
        assignment.lambdaAssignmentId = 101;
        assignment.lambdaId = 11;
        assignment.developerTeamId = 31;
        assignment.locationId = 21;
        assignment.organizationId = 41;
        assignment.version = new LambdaRuntimeVersion();
        assignment.version.lambdaVersionId = 51;
        return assignment;
    }

    private static void awaitOtherLambdaVersion(CountDownLatch started, List<String> fired, String assignment) {
        started.countDown();
        try {
            assertTrue(started.await(1, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
        fired.add(assignment);
    }

    private static User user(int userId, int organizationId) {
        User user = new User();
        user.userId = userId;
        user.organizationId = organizationId;
        return user;
    }

    private static Organization organization(int organizationId, int adminUserId) {
        Organization organization = new Organization();
        organization.organizationId = organizationId;
        organization.adminUserId = adminUserId;
        return organization;
    }

    private static <T> Object save(T[] destination, T value) {
        destination[0] = value;
        return null;
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[]{type},
            handler));
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

    private static final ConfirmingKeyedMessageProducer NOOP_PRODUCER = new ConfirmingKeyedMessageProducer() {
        @Override public void send(String topic, byte[] value) {
        }

        @Override public void send(String topic, String key, byte[] value) {
        }

        @Override public void sendAndAwait(String topic, byte[] value) {
        }

        @Override public void sendAndAwait(String topic, String key, byte[] value) {
        }

        @Override public void sendAndAwait(String topic, int key, byte[] value) {
        }
    };

    private static final class RecordingProducer implements ConfirmingKeyedMessageProducer {
        private String topic;
        private int key;
        private RuntimeException failure;

        @Override public void send(String topic, byte[] value) { throw new UnsupportedOperationException(); }
        @Override public void send(String topic, String key, byte[] value) { throw new UnsupportedOperationException(); }
        @Override public void sendAndAwait(String topic, byte[] value) { throw new UnsupportedOperationException(); }
        @Override public void sendAndAwait(String topic, String key, byte[] value) { throw new UnsupportedOperationException(); }

        @Override
        public void sendAndAwait(String topic, int key, byte[] value) {
            if (failure != null) throw failure;
            this.topic = topic;
            this.key = key;
        }
    }
}
