package dev.olegz.vf.core.service.lambda;

import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.util.concurrent.atomic.AtomicReference;

import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.dao.LambdaInvokeRetryOutboxDao;
import dev.olegz.vf.core.dao.LambdaRunStateDao;
import dev.olegz.vf.core.domain.lambdarun.LambdaInvokeRetryOutboxEntry;
import dev.olegz.vf.core.domain.lambdarun.LambdaRun;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LambdaRunStateServiceImplRetryTest {
    @BeforeAll
    static void oneTimeSetUp() {
        PropertyStore.start();
    }

    @Test
    void retryStateAndPayloadAreWrittenThroughTheSameServiceCall() {
        LambdaRun run = run();
        AtomicReference<Timestamp> expiry = new AtomicReference<>();
        AtomicReference<LambdaInvokeRetryOutboxEntry> inserted = new AtomicReference<>();
        LambdaRunStateDao stateDao = stateDao(run, expiry);
        LambdaInvokeRetryOutboxDao outboxDao = outboxDao(inserted);
        LambdaRunStateService service = new LambdaRunStateServiceImpl(stateDao, outboxDao);
        byte[] payload = new byte[]{1, 2, 3};
        long before = System.currentTimeMillis();

        LambdaRun.Status status = service.updateLambdaRunRetry(
            run.lambdaAssignmentId, run.invocationLane, run.runId, 3, 30_000L, payload);

        assertEquals(LambdaRun.Status.RETRY, status);
        assertNotNull(expiry.get());
        LambdaInvokeRetryOutboxEntry entry = inserted.get();
        assertNotNull(entry);
        assertEquals(run.lambdaAssignmentId, entry.lambdaAssignmentId);
        assertEquals(run.runId, entry.runId);
        assertEquals(3, entry.invocationGen);
        assertArrayEquals(payload, entry.payload);
        assertTrue(entry.retryAt.getTime() >= before);
        assertEquals(entry.retryAt.getTime() + 30_000L, expiry.get().getTime());
    }

    private static LambdaRun run() {
        LambdaRun run = new LambdaRun();
        run.lambdaAssignmentId = 17;
        run.invocationLane = InvocationLane.DEFAULT;
        run.runId = 1_750_000_000_000L;
        run.pendingCount = 0;
        return run;
    }

    private static LambdaRunStateDao stateDao(LambdaRun run, AtomicReference<Timestamp> expiry) {
        return (LambdaRunStateDao) Proxy.newProxyInstance(
            LambdaRunStateDao.class.getClassLoader(),
            new Class<?>[]{LambdaRunStateDao.class},
            (_, method, args) -> switch (method.getName()) {
                case "getLambdaRunForUpdate" -> run;
                case "updateLambdaRunForRetry" -> {
                    expiry.set((Timestamp) args[3]);
                    yield null;
                }
                default -> defaultValue(method.getReturnType());
            });
    }

    private static LambdaInvokeRetryOutboxDao outboxDao(AtomicReference<LambdaInvokeRetryOutboxEntry> inserted) {
        return (LambdaInvokeRetryOutboxDao) Proxy.newProxyInstance(
            LambdaInvokeRetryOutboxDao.class.getClassLoader(),
            new Class<?>[]{LambdaInvokeRetryOutboxDao.class},
            (_, method, args) -> switch (method.getName()) {
                case "exists" -> false;
                case "insert" -> {
                    inserted.set((LambdaInvokeRetryOutboxEntry) args[0]);
                    yield null;
                }
                default -> defaultValue(method.getReturnType());
            });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
