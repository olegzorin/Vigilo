package dev.olegz.vf.worker.service;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.dao.LambdaRunDao;
import dev.olegz.vf.core.domain.lambdarun.*;
import dev.olegz.vf.core.domain.lambdarun.input.TriggerEventData;
import dev.olegz.vf.core.event.ScheduledEvent;
import dev.olegz.vf.core.event.TriggerEvent;
import dev.olegz.vf.core.service.lambda.LambdaClientService;
import dev.olegz.vf.core.service.lambda.LambdaRunCompletionOutboxService;
import dev.olegz.vf.messaging.ConfirmingMessageProducer;
import dev.olegz.vf.messaging.Topics;
import dev.olegz.vf.worker.domain.LambdaInvokeRequest;
import dev.olegz.vf.worker.domain.LambdaOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LambdaRunServiceTest {

    @BeforeAll
    static void oneTimeSetUp() {
        PropertyStore.start();
    }

    @Test
    void synchronousInvocationRejectsAsyncLane() {
        LambdaRunService service = new LambdaRunService(null, null, null, new LambdaFunctionInvoker());
        LambdaInvokeRequest request = new LambdaInvokeRequest();
        request.lane = InvocationLane.ASYNC;

        try {
            assertThrows(IllegalArgumentException.class, () -> service.executeDefaultLaneLambdaRequest(request));
        } finally {
            service.shutdown();
        }
    }

    @Test
    void asynchronousResultCompletesRunDirectly() {
        LambdaRunContext[] completed = new LambdaRunContext[1];
        LambdaRunDao runDao = proxy(LambdaRunDao.class, (_, _, _) -> null);
        LambdaRunService service = new LambdaRunService(null, runDao, null, new LambdaFunctionInvoker()) {
            @Override
            public void processRunCompletion(LambdaRunContext context) {
                completed[0] = context;
            }
        };
        LambdaInvokeRequest request = new LambdaInvokeRequest();
        request.lambdaAssignmentId = 101;
        request.requestId = System.currentTimeMillis();
        request.lane = InvocationLane.ASYNC;
        LambdaOutput output = new LambdaOutput();
        output.startCode = 0;

        try {
            service.processAsyncResult(request, output);
        } finally {
            service.shutdown();
        }

        assertEquals(101, completed[0].lambdaAssignmentId);
        assertEquals(request.requestId, completed[0].requestId);
        assertEquals(InvocationLane.ASYNC, completed[0].lane);
    }

    @Test
    void asynchronousResultPersistenceFailureDoesNotCompleteRun() {
        LambdaRunContext[] completed = new LambdaRunContext[1];
        LambdaRunDao runDao = proxy(LambdaRunDao.class, (_, method, _) -> {
            if (method.getName().equals("insertRunInfo")) throw new RuntimeException("database unavailable");
            return null;
        });
        LambdaRunService service = new LambdaRunService(null, runDao, null, new LambdaFunctionInvoker()) {
            @Override
            public void processRunCompletion(LambdaRunContext context) {
                completed[0] = context;
            }
        };
        LambdaInvokeRequest request = new LambdaInvokeRequest();
        request.lambdaAssignmentId = 101;
        request.requestId = System.currentTimeMillis();
        request.lane = InvocationLane.ASYNC;
        LambdaOutput output = new LambdaOutput();
        output.startCode = 0;

        try {
            assertThrows(RuntimeException.class, () -> service.processAsyncResult(request, output));
        } finally {
            service.shutdown();
        }

        assertNull(completed[0]);
    }

    @Test
    void duplicateAsynchronousResultDoesNotCompleteRun() {
        LambdaRunContext[] completed = new LambdaRunContext[1];
        LambdaRunService service = new LambdaRunService(null, null, null, new LambdaFunctionInvoker()) {
            @Override
            public void processRunCompletion(LambdaRunContext context) {
                completed[0] = context;
            }
        };
        LambdaInvokeRequest request = new LambdaInvokeRequest();
        request.lambdaAssignmentId = 101;
        request.requestId = 123_000L;
        request.lane = InvocationLane.ASYNC;
        LambdaOutput output = new LambdaOutput();
        output.startCode = 1;

        try {
            service.processAsyncResult(request, output);
        } finally {
            service.shutdown();
        }

        assertNull(completed[0]);
    }

    @Test
    void asynchronousSubmissionInvokesOnlyTheCurrentUnsubmittedRun() {
        LambdaRun current = new LambdaRun();
        current.lambdaAssignmentId = 101;
        current.invocationLane = InvocationLane.ASYNC;
        current.runId = 123_000L;
        int[] sentUpdates = new int[1];
        LambdaRunDao runDao = proxy(LambdaRunDao.class, (_, method, args) -> switch (method.getName()) {
            case "getLambdaRun" -> current;
            case "updateLambdaRunSent" -> {
                sentUpdates[0]++;
                yield true;
            }
            default -> null;
        });
        RecordingLambdaFunctionInvoker invoker = new RecordingLambdaFunctionInvoker();
        LambdaRunService service = new LambdaRunService(null, runDao, null, invoker);
        LambdaInvokeRequest request = new LambdaInvokeRequest();
        request.lambdaAssignmentId = current.lambdaAssignmentId;
        request.requestId = current.runId;
        request.lane = InvocationLane.ASYNC;
        request.lambdaFunction = "test-function";
        request.appInput = "{}";

        try {
            assertTrue(service.sendAsyncRequest(request));
            request.requestId++;
            assertFalse(service.sendAsyncRequest(request));
        } finally {
            service.shutdown();
        }

        assertEquals(1, invoker.invocations);
        assertEquals(1, sentUpdates[0]);
    }

    @Test
    void admittedInputUsesConfirmingKafkaPublicationWithThePersistedRunId() {
        LambdaRuntimeAssignment assignment = new LambdaRuntimeAssignment();
        assignment.lambdaAssignmentId = 101;
        assignment.lambdaId = 7;
        assignment.locationId = 21;
        assignment.version = new LambdaRuntimeVersion();
        assignment.version.lambdaVersionId = 31;
        assignment.version.functionName = "lambda-function";
        assignment.version.timeout = 30;
        LambdaRun persisted = new LambdaRun();
        persisted.lambdaAssignmentId = assignment.lambdaAssignmentId;
        persisted.invocationLane = InvocationLane.DEFAULT;
        persisted.runId = 123_000L;
        persisted.lambdaVersionId = assignment.version.lambdaVersionId;
        persisted.functionName = assignment.version.functionName;
        persisted.triggerCount = 1;
        LambdaRunDao runDao = proxy(LambdaRunDao.class, (proxy, method, args) ->
            method.getName().equals("getLambdaRun") ? persisted : null);
        LambdaClientService clientService = proxy(LambdaClientService.class, (proxy, method, args) ->
            method.getName().equals("createLambdaKey") ? new LambdaKeyInput("key", Long.MAX_VALUE) : null);
        RecordingProducer producer = new RecordingProducer();
        LambdaRunService service = new LambdaRunService(
            clientService, runDao, null, new LambdaFunctionInvoker(), producer);
        TriggerEvent event = new TriggerEvent(TriggerEvent.TRIGGER_LOCATION_EVENT, assignment.locationId);

        try {
            assertTrue(service.publishAdmittedLambdaInput(
                assignment, event, new TriggerEventData(), persisted.runId));
        } finally {
            service.shutdown();
        }

        assertEquals(Topics.LAMBDA_INVOKE_REQUEST, producer.topic);
        assertEquals(1, producer.confirmedSends);
        assertEquals(0, producer.asyncSends);
    }

    @Test
    void admittedScheduledInputUsesConfirmingKafkaPublication() {
        LambdaRuntimeAssignment assignment = new LambdaRuntimeAssignment();
        assignment.lambdaAssignmentId = 101;
        assignment.lambdaId = 7;
        assignment.locationId = 21;
        assignment.version = new LambdaRuntimeVersion();
        assignment.version.lambdaVersionId = 31;
        assignment.version.asyncFunctionName = "lambda-function-async";
        assignment.version.timeout = 30;
        LambdaRun persisted = new LambdaRun();
        persisted.lambdaAssignmentId = assignment.lambdaAssignmentId;
        persisted.invocationLane = InvocationLane.ASYNC;
        persisted.runId = 123_000L;
        persisted.lambdaVersionId = assignment.version.lambdaVersionId;
        persisted.functionName = assignment.version.asyncFunctionName;
        persisted.triggerCount = 1;
        LambdaRunDao runDao = proxy(LambdaRunDao.class, (proxy, method, args) ->
            method.getName().equals("getLambdaRun") ? persisted : null);
        LambdaClientService clientService = proxy(LambdaClientService.class, (proxy, method, args) ->
            method.getName().equals("createLambdaKey") ? new LambdaKeyInput("key", Long.MAX_VALUE) : null);
        RecordingProducer producer = new RecordingProducer();
        LambdaRunService service = new LambdaRunService(
            clientService, runDao, null, new LambdaFunctionInvoker(), producer);
        ScheduledEvent event = new ScheduledEvent(assignment.locationId, assignment.lambdaAssignmentId, List.of("morning"));

        try {
            assertTrue(service.publishAdmittedLambdaInput(
                assignment, event, new TriggerEventData(), persisted.runId));
        } finally {
            service.shutdown();
        }

        assertEquals(Topics.LAMBDA_INVOKE_REQUEST, producer.topic);
        assertEquals(0, producer.asyncSends);
        assertEquals(1, producer.confirmedSends);
    }

    @Test
    void finalResultPersistsCompletionBeforeAcknowledgingInvocation() {
        List<String> operations = new ArrayList<>();
        LambdaRunContext[] persisted = new LambdaRunContext[1];
        LambdaRunCompletionOutboxService outboxService = proxy(
            LambdaRunCompletionOutboxService.class,
            (_, method, args) -> {
                if (method.getName().equals("enqueue")) {
                    persisted[0] = (LambdaRunContext) args[0];
                    operations.add("persist");
                    return null;
                }
                return null;
            });
        LambdaRunService service = new LambdaRunService(
            null, null, null, new LambdaFunctionInvoker(), null, outboxService, new RecordingProducer());
        LambdaInvokeRequest request = new LambdaInvokeRequest();
        request.lambdaAssignmentId = 101;
        request.requestId = System.currentTimeMillis();
        request.lane = InvocationLane.DEFAULT;

        try {
            service.processDefaultLaneInvocationOutcome(
                dev.olegz.vf.worker.domain.LambdaInvocationOutcome.finalResult(request),
                () -> operations.add("acknowledge"));
        } finally {
            service.shutdown();
        }

        assertEquals(List.of("persist", "acknowledge"), operations);
        assertEquals(101, persisted[0].lambdaAssignmentId);
        assertEquals(request.requestId, persisted[0].requestId);
        assertEquals(InvocationLane.DEFAULT, persisted[0].lane);
    }

    @Test
    void finalResultPersistenceFailureLeavesInvocationUnacknowledged() {
        LambdaRunCompletionOutboxService outboxService = proxy(
            LambdaRunCompletionOutboxService.class,
            (_, method, _) -> {
                if (method.getName().equals("enqueue")) throw new RuntimeException("database unavailable");
                return null;
            });
        LambdaRunService service = new LambdaRunService(
            null, null, null, new LambdaFunctionInvoker(), null, outboxService, new RecordingProducer());
        LambdaInvokeRequest request = new LambdaInvokeRequest();
        request.lambdaAssignmentId = 101;
        request.requestId = 123_000L;
        request.lane = InvocationLane.DEFAULT;
        boolean[] acknowledged = new boolean[1];

        try {
            assertThrows(RuntimeException.class, () -> service.processDefaultLaneInvocationOutcome(
                dev.olegz.vf.worker.domain.LambdaInvocationOutcome.finalResult(request),
                () -> acknowledged[0] = true));
        } finally {
            service.shutdown();
        }

        assertEquals(false, acknowledged[0]);
    }

    private static final class RecordingProducer implements ConfirmingMessageProducer {
        private int asyncSends;
        private int confirmedSends;
        private String topic;

        @Override public void send(String topic, byte[] value) { asyncSends++; }
        @Override public void sendAndAwait(String topic, byte[] value) {
            this.topic = topic;
            confirmedSends++;
        }
    }

    private static final class RecordingLambdaFunctionInvoker extends LambdaFunctionInvoker {
        private int invocations;

        @Override
        String invokeAsync(String functionName, String input) {
            invocations++;
            return "aws-request-id";
        }
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
