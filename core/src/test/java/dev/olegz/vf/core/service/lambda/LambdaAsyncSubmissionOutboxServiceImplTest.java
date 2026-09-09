package dev.olegz.vf.core.service.lambda;

import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.dao.LambdaAsyncSubmissionOutboxDao;
import dev.olegz.vf.core.domain.lambdarun.LambdaAsyncSubmissionOutboxEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LambdaAsyncSubmissionOutboxServiceImplTest {

    @BeforeAll
    static void oneTimeSetUp() {
        PropertyStore.start();
    }

    @Test
    void durablyEnqueuesClaimsCompletesAndReschedulesSubmissions() {
        AtomicReference<LambdaAsyncSubmissionOutboxEntry> inserted = new AtomicReference<>();
        AtomicReference<LambdaAsyncSubmissionOutboxEntry> claimed = new AtomicReference<>();
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger rescheduled = new AtomicInteger();
        LambdaAsyncSubmissionOutboxEntry due = new LambdaAsyncSubmissionOutboxEntry(
            17, 1_750_000_000_000L, new byte[]{1},
            new Timestamp(System.currentTimeMillis() - 1), "initial failure");
        LambdaAsyncSubmissionOutboxDao dao = (LambdaAsyncSubmissionOutboxDao) Proxy.newProxyInstance(
            LambdaAsyncSubmissionOutboxDao.class.getClassLoader(),
            new Class<?>[]{LambdaAsyncSubmissionOutboxDao.class},
            (_, method, args) -> switch (method.getName()) {
                case "exists" -> false;
                case "insert" -> {
                    inserted.set((LambdaAsyncSubmissionOutboxEntry) args[0]);
                    yield null;
                }
                case "claimNextDue" -> {
                    due.claimId = (String) args[1];
                    due.claimUntil = (Timestamp) args[2];
                    claimed.set(due);
                    yield due;
                }
                case "deleteClaimed" -> {
                    completed.incrementAndGet();
                    yield true;
                }
                case "rescheduleClaimed" -> {
                    rescheduled.incrementAndGet();
                    yield true;
                }
                default -> defaultValue(method.getReturnType());
            });
        LambdaAsyncSubmissionOutboxService service = new LambdaAsyncSubmissionOutboxServiceImpl(dao);

        service.enqueue(17, 1_750_000_000_000L, new byte[]{1}, new RuntimeException("AWS unavailable"));
        LambdaAsyncSubmissionOutboxEntry entry = service.claimNextDue();

        assertNotNull(inserted.get());
        assertTrue(inserted.get().retryAt.getTime() > System.currentTimeMillis());
        assertEquals("AWS unavailable", inserted.get().lastError);
        assertSame(due, entry);
        assertSame(entry, claimed.get());
        assertNotNull(entry.claimId);
        assertNotNull(entry.claimUntil);
        assertTrue(service.completeClaim(entry));
        assertTrue(service.rescheduleClaim(entry, new RuntimeException("still unavailable")));
        assertEquals("still unavailable", entry.lastError);
        assertEquals(1, completed.get());
        assertEquals(1, rescheduled.get());
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
