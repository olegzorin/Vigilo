package dev.olegz.vf.core.service.lambda;

import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.dao.LambdaInvokeRetryOutboxDao;
import dev.olegz.vf.core.dao.LambdaRunStateDao;
import dev.olegz.vf.core.domain.lambdarun.LambdaError;
import dev.olegz.vf.core.domain.lambdarun.LambdaPendingInputRecord;
import dev.olegz.vf.core.domain.lambdarun.LambdaRun;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import dev.olegz.vf.core.domain.lambdarun.input.TriggerEventData;
import dev.olegz.vf.core.event.ResetEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LambdaRunStateServiceImplTest {
    @BeforeAll
    static void oneTimeSetUp() {
        PropertyStore.start();
    }

    @Test
    void continuationIdentityIsStableAndMonotonic() {
        TriggerEventData laterInput = input(125_000L);
        TriggerEventData olderInput = input(122_000L);

        assertEquals(125_000L, LambdaRunStateServiceImpl.continuationRunId(123_000L, laterInput));
        assertEquals(123_001L, LambdaRunStateServiceImpl.continuationRunId(123_000L, olderInput));
        assertEquals(
            LambdaRunStateServiceImpl.continuationRunId(123_000L, laterInput),
            LambdaRunStateServiceImpl.continuationRunId(123_000L, laterInput));
    }

    @Test
    void expiredRunQueuesNewInputUntilCompletionRecovery() {
        LambdaRun expired = run(123_000L, 0, System.currentTimeMillis() - 1L);
        AtomicBoolean replaced = new AtomicBoolean();
        AtomicBoolean inputStored = new AtomicBoolean();
        AtomicInteger pendingCount = new AtomicInteger();
        long[] nextInputNumber = {0L};
        LambdaRunStateDao stateDao = stateDao(expired, replaced, inputStored, pendingCount, nextInputNumber);
        LambdaRunStateServiceImpl service = new LambdaRunStateServiceImpl(stateDao, retryOutboxDao());

        boolean started = service.putNewRun(run(124_000L, 2, System.currentTimeMillis() + 60_000L));

        assertFalse(started);
        assertFalse(replaced.get());
        assertTrue(inputStored.get());
        assertEquals(1, pendingCount.get());
        assertEquals(expired.runId * LambdaRun.MAX_PENDING_INPUTS, nextInputNumber[0]);
    }

    @Test
    void resetStillReplacesExpiredRun() {
        LambdaRun expired = run(123_000L, 0, System.currentTimeMillis() - 1L);
        AtomicBoolean replaced = new AtomicBoolean();
        LambdaRunStateDao stateDao = stateDao(
            expired, replaced, new AtomicBoolean(), new AtomicInteger(), new long[1]);
        LambdaRunStateServiceImpl service = new LambdaRunStateServiceImpl(stateDao, retryOutboxDao());
        LambdaRun reset = run(124_000L, ResetEvent.TRIGGER, System.currentTimeMillis() + 60_000L);

        assertTrue(service.putNewRun(reset));
        assertTrue(replaced.get());
    }

    @Test
    void registersLambdaErrorWhenPendingInputOverflows() {
        LambdaRun current = run(123_000L, 0, System.currentTimeMillis() + 60_000L);
        current.pendingCount = current.maxPendingInputs();
        AtomicBoolean inputStored = new AtomicBoolean();
        LambdaRunStateDao stateDao = stateDao(
            current, new AtomicBoolean(), inputStored, new AtomicInteger(), new long[1]);
        AtomicReference<LambdaError> registeredError = new AtomicReference<>();
        LambdaRunStateServiceImpl service = new LambdaRunStateServiceImpl(
            stateDao, retryOutboxDao(), registeredError::set);
        LambdaRun rejected = run(124_000L, 2, System.currentTimeMillis() + 60_000L);

        assertFalse(service.putNewRun(rejected));

        assertFalse(inputStored.get());
        LambdaError error = registeredError.get();
        assertEquals(rejected.lambdaAssignmentId, error.lambdaAssignmentId);
        assertEquals(rejected.invocationLane, error.lane);
        assertEquals(rejected.triggeredAt.getTime(), error.time);
        assertEquals(
            "Too many pending inputs; the lambda input was discarded because the pending execution limit was reached",
            error.message);
    }

    private static TriggerEventData input(long time) {
        TriggerEventData input = new TriggerEventData();
        input.time = time;
        return input;
    }

    private static LambdaRun run(long runId, int trigger, long expiryTime) {
        LambdaRun run = new LambdaRun();
        run.lambdaAssignmentId = 101;
        run.invocationLane = InvocationLane.DEFAULT;
        run.lambdaId = 7;
        run.lambdaVersionId = 11;
        run.locationId = 21;
        run.runId = runId;
        run.trigger = trigger;
        run.inputs = new TriggerEventData[]{input(runId)};
        run.triggeredAt = new Timestamp(runId);
        run.expiryDate = new Timestamp(expiryTime);
        return run;
    }

    private static LambdaRunStateDao stateDao(
        LambdaRun current,
        AtomicBoolean replaced,
        AtomicBoolean inputStored,
        AtomicInteger pendingCount,
        long[] nextInputNumber)
    {
        return (LambdaRunStateDao) Proxy.newProxyInstance(
            LambdaRunStateDao.class.getClassLoader(),
            new Class<?>[]{LambdaRunStateDao.class},
            (_, method, args) -> switch (method.getName()) {
                case "getLambdaRunForUpdate" -> current;
                case "updateLambdaRun" -> {
                    replaced.set(true);
                    yield true;
                }
                case "enqueuePendingInput" -> {
                    inputStored.set(true);
                    LambdaPendingInputRecord input = (LambdaPendingInputRecord) args[2];
                    nextInputNumber[0] = input.inputNumber;
                    yield null;
                }
                case "updateLambdaRunPendingCount" -> {
                    pendingCount.set((Integer) args[2]);
                    yield null;
                }
                default -> defaultValue(method.getReturnType());
            });
    }

    private static LambdaInvokeRetryOutboxDao retryOutboxDao() {
        return (LambdaInvokeRetryOutboxDao) Proxy.newProxyInstance(
            LambdaInvokeRetryOutboxDao.class.getClassLoader(),
            new Class<?>[]{LambdaInvokeRetryOutboxDao.class},
            (_, method, _) -> defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
