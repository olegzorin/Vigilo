package dev.olegz.vf.worker.service;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.dao.LambdaRunDao;
import dev.olegz.vf.core.dao.LambdaVariableDao;
import dev.olegz.vf.core.domain.lambdarun.LambdaEventReceipt;
import dev.olegz.vf.core.domain.lambdarun.LambdaRun;
import dev.olegz.vf.core.domain.lambdarun.LambdaRuntimeAssignment;
import dev.olegz.vf.core.domain.lambdarun.LambdaRuntimeVersion;
import dev.olegz.vf.core.domain.lambdarun.input.TriggerEventData;
import dev.olegz.vf.core.event.ResetEvent;
import dev.olegz.vf.core.event.ScheduledEvent;
import dev.olegz.vf.core.event.TriggerEvent;
import dev.olegz.vf.core.service.lambda.LambdaEventReceiptService;
import dev.olegz.vf.core.service.lambda.TriggerEventDataService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LambdaTriggeringServiceTest {

    @BeforeAll
    static void oneTimeSetUp() {
        PropertyStore.start();
    }

    @Test
    void resetDispatchesOnlyTheIdentifiedAssignment() {
        LambdaRuntimeAssignment assignment = assignment(101);
        assignment.locationId = 21;
        ResetEvent event = new ResetEvent(21, assignment.lambdaAssignmentId);
        event.time = 123_456L;
        event.variableGeneration = 789_012L;
        TriggerEventData eventData = new TriggerEventData();
        RecordingLambdaRunService lambdaRunService = new RecordingLambdaRunService();
        LambdaRunDao lambdaRunDao = proxy(LambdaRunDao.class, (proxy, method, args) ->
            method.getName().equals("getActiveLambdaRuntimeAssignment") ? assignment : defaultValue(method.getReturnType()));
        TriggerEventDataService dataService = new TriggerEventDataService() {
            @Override public TriggerEventData hydrate(ResetEvent resetEvent) {
                assertSame(event, resetEvent);
                return eventData;
            }

            @Override public TriggerEventData hydrate(ScheduledEvent scheduledEvent) {
                throw new AssertionError("Reset must not use scheduled-event hydration");
            }

            @Override public TriggerEventData hydrate(TriggerEvent triggerEvent) {
                throw new AssertionError("Reset must not use generic trigger hydration");
            }
        };
        long[] generation = new long[1];
        LambdaVariableDao variableDao = proxy(LambdaVariableDao.class, (proxy, method, args) -> {
            if (method.getName().equals("advanceLambdaAssignmentVariableGeneration")) {
                assertEquals(assignment.lambdaAssignmentId, args[0]);
                generation[0] = (long) args[1];
            }
            return defaultValue(method.getReturnType());
        });
        LambdaTriggeringService service =
            new LambdaTriggeringService(lambdaRunDao, lambdaRunService, variableDao, null, dataService);

        service.process(event, (run, triggeredAssignment) -> {});

        assertSame(assignment, lambdaRunService.assignment);
        assertSame(event, lambdaRunService.event);
        assertSame(eventData, lambdaRunService.eventData);
        assertEquals(event.variableGeneration, generation[0]);
    }

    @Test
    void scheduleDispatchesOnlyTheIdentifiedAssignment() {
        LambdaRuntimeAssignment assignment = assignment(101);
        assignment.locationId = 21;
        ScheduledEvent event = new ScheduledEvent(
            assignment.locationId,
            assignment.lambdaAssignmentId,
            List.of("morning"));
        TriggerEventData eventData = new TriggerEventData();
        RecordingLambdaRunService lambdaRunService = new RecordingLambdaRunService();
        LambdaRunDao lambdaRunDao = proxy(LambdaRunDao.class, (proxy, method, args) ->
            method.getName().equals("getActiveLambdaRuntimeAssignment") ? assignment : defaultValue(method.getReturnType()));
        TriggerEventDataService dataService = new TriggerEventDataService() {
            @Override public TriggerEventData hydrate(ResetEvent resetEvent) {
                throw new AssertionError("Schedule must not use reset hydration");
            }

            @Override public TriggerEventData hydrate(ScheduledEvent scheduledEvent) {
                assertSame(event, scheduledEvent);
                return eventData;
            }

            @Override public TriggerEventData hydrate(TriggerEvent triggerEvent) {
                throw new AssertionError("Schedule must not use generic trigger hydration");
            }
        };
        LambdaTriggeringService service = new LambdaTriggeringService(
            lambdaRunDao,
            lambdaRunService,
            proxy(LambdaVariableDao.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            new RecordingReceiptService(),
            dataService);

        service.processDurable("schedule-1", event);

        assertSame(assignment, lambdaRunService.assignment);
        assertSame(event, lambdaRunService.scheduledEvent);
        assertSame(eventData, lambdaRunService.eventData);
    }

    @Test
    void scheduleIgnoresAssignmentFromAnotherLocation() {
        LambdaRuntimeAssignment assignment = assignment(101);
        assignment.locationId = 22;
        ScheduledEvent event = new ScheduledEvent(21, assignment.lambdaAssignmentId, List.of("morning"));
        RecordingLambdaRunService lambdaRunService = new RecordingLambdaRunService();
        LambdaRunDao lambdaRunDao = proxy(LambdaRunDao.class, (proxy, method, args) ->
            method.getName().equals("getActiveLambdaRuntimeAssignment") ? assignment : defaultValue(method.getReturnType()));
        TriggerEventDataService dataService = new TriggerEventDataService() {
            @Override public TriggerEventData hydrate(ResetEvent resetEvent) {
                throw new AssertionError("Rejected event must not be hydrated");
            }

            @Override public TriggerEventData hydrate(ScheduledEvent scheduledEvent) {
                throw new AssertionError("Rejected event must not be hydrated");
            }

            @Override public TriggerEventData hydrate(TriggerEvent triggerEvent) {
                throw new AssertionError("Rejected event must not be hydrated");
            }
        };
        LambdaVariableDao variableDao = proxy(LambdaVariableDao.class, (proxy, method, args) -> {
            if (method.getName().equals("advanceLambdaAssignmentVariableGeneration")) {
                throw new AssertionError("Rejected reset advanced the variable generation");
            }
            return defaultValue(method.getReturnType());
        });
        LambdaTriggeringService service =
            new LambdaTriggeringService(lambdaRunDao, lambdaRunService, variableDao,
                new RecordingReceiptService(), dataService);

        service.processDurable("schedule-1", event);

        assertNull(lambdaRunService.assignment);
    }

    @Test
    void dispatchesSameHydratedDataToEveryAssignment() {
        LambdaRuntimeAssignment first = assignment(1);
        LambdaRuntimeAssignment second = assignment(2);
        TriggerEventData eventData = new TriggerEventData();
        List<TriggerEventData> submittedData = new ArrayList<>();

        LambdaTriggeringService.dispatchSharedEventData(
            List.of(first, second),
            eventData,
            (assignment, data) -> submittedData.add(data));

        assertEquals(2, submittedData.size());
        assertSame(eventData, submittedData.get(0));
        assertSame(eventData, submittedData.get(1));
    }

    @Test
    void durableFanOutContinuesAfterPublicationFailureAndRetriesOnlyIncompleteAssignments() {
        LambdaRuntimeAssignment first = assignment(1);
        first.lambdaId = 101;
        first.locationId = 21;
        LambdaRuntimeAssignment second = assignment(2);
        second.lambdaId = 102;
        second.locationId = 21;
        TriggerEvent event = new TriggerEvent(TriggerEvent.TRIGGER_LOCATION_EVENT, 21);
        TriggerEventData eventData = new TriggerEventData();
        eventData.trigger = event.trigger;
        int[] selections = new int[1];
        LambdaRunDao lambdaRunDao = proxy(LambdaRunDao.class, (proxy, method, args) -> {
            if (method.getName().equals("getRuntimeAssignmentsForTrigger")) {
                selections[0]++;
                return List.of(first, second);
            }
            if (method.getName().equals("getActiveLambdaRuntimeAssignment")) {
                return ((int) args[0]) == first.lambdaAssignmentId ? first : second;
            }
            return defaultValue(method.getReturnType());
        });
        TriggerEventDataService dataService = new TriggerEventDataService() {
            @Override public TriggerEventData hydrate(TriggerEvent triggerEvent) { return eventData; }
            @Override public TriggerEventData hydrate(ResetEvent resetEvent) { throw new AssertionError(); }
            @Override public TriggerEventData hydrate(ScheduledEvent scheduledEvent) { throw new AssertionError(); }
        };
        RecordingLambdaRunService runService = new RecordingLambdaRunService();
        runService.failPublishAssignmentId = first.lambdaAssignmentId;
        RecordingReceiptService receipts = new RecordingReceiptService();
        LambdaTriggeringService service = new LambdaTriggeringService(
            lambdaRunDao,
            runService,
            proxy(LambdaVariableDao.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
            receipts,
            dataService);

        assertThrows(RuntimeException.class, () -> service.processDurable("event-1", event));
        assertEquals(List.of(second.lambdaAssignmentId), receipts.completed);
        assertEquals(List.of(first.lambdaAssignmentId, second.lambdaAssignmentId), runService.durableAttempts);

        runService.failPublishAssignmentId = 0;
        service.processDurable("event-1", event);

        assertEquals(1, selections[0], "the assignment set must be frozen on first delivery");
        assertEquals(List.of(second.lambdaAssignmentId, first.lambdaAssignmentId), receipts.completed);
        assertEquals(List.of(first.lambdaAssignmentId, second.lambdaAssignmentId), runService.durableAttempts);
        assertEquals(List.of(first.lambdaAssignmentId, second.lambdaAssignmentId, first.lambdaAssignmentId),
            runService.publishAttempts);
        assertEquals(1, receipts.completedEvents);
    }

    @Test
    void deduplicationDoesNotMutateSourceAndKeepsOldestAssignmentWithNewestVersion() {
        LambdaRuntimeAssignment oldest = assignment(10);
        oldest.lambdaId = 7;
        oldest.locationId = 21;
        oldest.version.lambdaVersionId = 100;

        LambdaRuntimeAssignment newerAssignment = assignment(11);
        newerAssignment.lambdaId = 7;
        newerAssignment.locationId = 21;
        newerAssignment.version.lambdaVersionId = 200;

        LambdaRuntimeAssignment newerVersion = assignment(10);
        newerVersion.lambdaId = 7;
        newerVersion.locationId = 21;
        newerVersion.version.lambdaVersionId = 101;

        LambdaRuntimeAssignment otherLambda = assignment(12);
        otherLambda.lambdaId = 8;
        otherLambda.locationId = 21;

        List<LambdaRuntimeAssignment> source =
            List.of(oldest, newerAssignment, newerVersion, otherLambda);
        List<LambdaRuntimeAssignment> result = LambdaTriggeringService.cleanLambdaAssignments(source);

        assertEquals(4, source.size());
        assertEquals(2, result.size());
        assertSame(newerVersion, result.get(0));
        assertSame(otherLambda, result.get(1));
    }

    private static LambdaRuntimeAssignment assignment(int lambdaAssignmentId) {
        LambdaRuntimeAssignment assignment = new LambdaRuntimeAssignment();
        assignment.lambdaAssignmentId = lambdaAssignmentId;
        assignment.version = new LambdaRuntimeVersion();
        return assignment;
    }

    private static final class RecordingLambdaRunService extends LambdaRunService {
        private LambdaRuntimeAssignment assignment;
        private ResetEvent event;
        private ScheduledEvent scheduledEvent;
        private TriggerEventData eventData;
        private int failPublishAssignmentId;
        private final List<Integer> durableAttempts = new ArrayList<>();
        private final List<Integer> publishAttempts = new ArrayList<>();

        private RecordingLambdaRunService() {
            super(null, null, null, null);
        }

        @Override
        void submitLambdaInput(
            LambdaRuntimeAssignment assignment,
            ResetEvent event,
            TriggerEventData eventData,
            BiConsumer<LambdaRun, LambdaRuntimeAssignment> execConsumer)
        {
            this.assignment = assignment;
            this.event = event;
            this.eventData = eventData;
        }

        @Override
        long admitDurableLambdaInput(
            LambdaRuntimeAssignment assignment,
            TriggerEvent event,
            TriggerEventData eventData)
        {
            durableAttempts.add(assignment.lambdaAssignmentId);
            this.assignment = assignment;
            this.eventData = eventData;
            return assignment.lambdaAssignmentId * 100L;
        }

        @Override
        boolean publishAdmittedLambdaInput(
            LambdaRuntimeAssignment assignment,
            TriggerEvent event,
            TriggerEventData eventData,
            long runId)
        {
            publishAttempts.add(assignment.lambdaAssignmentId);
            if (assignment.lambdaAssignmentId == failPublishAssignmentId) {
                throw new RuntimeException("transient publication failure");
            }
            return true;
        }

        @Override
        long admitDurableLambdaInput(
            LambdaRuntimeAssignment assignment,
            ScheduledEvent event,
            TriggerEventData eventData)
        {
            durableAttempts.add(assignment.lambdaAssignmentId);
            this.assignment = assignment;
            this.scheduledEvent = event;
            this.eventData = eventData;
            return assignment.lambdaAssignmentId * 100L;
        }

        @Override
        boolean publishAdmittedLambdaInput(
            LambdaRuntimeAssignment assignment,
            ScheduledEvent event,
            TriggerEventData eventData,
            long runId)
        {
            publishAttempts.add(assignment.lambdaAssignmentId);
            return true;
        }
    }

    private static final class RecordingReceiptService implements LambdaEventReceiptService {
        private List<Integer> assignments;
        private final List<Integer> completed = new ArrayList<>();
        private final Map<Integer, LambdaEventReceipt> receipts = new HashMap<>();
        private int completedEvents;

        @Override
        public List<Integer> initialize(String eventId, java.util.function.Supplier<List<Integer>> supplier) {
            if (assignments == null) assignments = List.copyOf(supplier.get());
            return assignments;
        }

        @Override
        public LambdaEventReceipt admitAssignment(
            String eventId,
            int lambdaAssignmentId,
            java.util.function.LongSupplier admitter)
        {
            LambdaEventReceipt receipt = receipts.computeIfAbsent(lambdaAssignmentId, id -> {
                LambdaEventReceipt value = new LambdaEventReceipt();
                value.eventId = eventId;
                value.lambdaAssignmentId = id;
                value.status = LambdaEventReceipt.PENDING;
                return value;
            });
            if (receipt.completed() || LambdaEventReceipt.ADMITTED.equals(receipt.status)) return receipt;
            long runId = admitter.getAsLong();
            if (runId > 0) {
                receipt.status = LambdaEventReceipt.ADMITTED;
                receipt.runId = runId;
            } else {
                receipt.status = LambdaEventReceipt.COMPLETED;
                completed.add(lambdaAssignmentId);
            }
            return receipt;
        }

        @Override
        public void completeAssignment(String eventId, int lambdaAssignmentId, long runId) {
            LambdaEventReceipt receipt = receipts.get(lambdaAssignmentId);
            receipt.status = LambdaEventReceipt.COMPLETED;
            completed.add(lambdaAssignmentId);
        }
        @Override public void completeEvent(String eventId) { completedEvents++; }
        @Override public int deleteCompletedBefore(java.sql.Timestamp cutoff, int limit) { return 0; }
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
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
