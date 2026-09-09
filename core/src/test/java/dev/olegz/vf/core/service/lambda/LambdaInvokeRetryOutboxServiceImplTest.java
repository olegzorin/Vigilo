package dev.olegz.vf.core.service.lambda;

import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.dao.LambdaInvokeRetryOutboxDao;
import dev.olegz.vf.core.domain.lambdarun.LambdaInvokeRetryOutboxEntry;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LambdaInvokeRetryOutboxServiceImplTest {
    @BeforeAll
    static void oneTimeSetUp() {
        PropertyStore.start();
    }

    @Test
    void claimsDueEntryWithLeaseAndUsesClaimForTerminalUpdates() {
        LambdaInvokeRetryOutboxEntry due = new LambdaInvokeRetryOutboxEntry(
            17,
            InvocationLane.DEFAULT,
            1_750_000_000_000L,
            2,
            new byte[]{1},
            new Timestamp(System.currentTimeMillis() - 1));
        AtomicReference<LambdaInvokeRetryOutboxEntry> claimed = new AtomicReference<>();
        AtomicInteger renewed = new AtomicInteger();
        AtomicInteger deleted = new AtomicInteger();
        AtomicInteger released = new AtomicInteger();
        LambdaInvokeRetryOutboxDao dao = (LambdaInvokeRetryOutboxDao) Proxy.newProxyInstance(
            LambdaInvokeRetryOutboxDao.class.getClassLoader(),
            new Class<?>[]{LambdaInvokeRetryOutboxDao.class},
            (_, method, args) -> switch (method.getName()) {
                case "claimNextDue" -> {
                    due.claimId = (String) args[1];
                    due.claimUntil = (Timestamp) args[2];
                    claimed.set(due);
                    yield due;
                }
                case "deleteClaimed" -> {
                    deleted.incrementAndGet();
                    yield true;
                }
                case "renewClaim" -> {
                    renewed.incrementAndGet();
                    yield true;
                }
                case "releaseClaim" -> {
                    released.incrementAndGet();
                    yield true;
                }
                default -> defaultValue(method.getReturnType());
            });
        LambdaInvokeRetryOutboxService service = new LambdaInvokeRetryOutboxServiceImpl(dao);
        long before = System.currentTimeMillis();

        LambdaInvokeRetryOutboxEntry entry = service.claimNextDue();

        assertSame(due, entry);
        assertSame(entry, claimed.get());
        assertNotNull(entry.claimId);
        assertTrue(entry.claimUntil.getTime() > before);
        Timestamp initialClaimUntil = entry.claimUntil;
        assertTrue(service.renewClaim(entry));
        assertTrue(entry.claimUntil.getTime() >= initialClaimUntil.getTime());
        assertTrue(service.completeClaim(entry));
        assertTrue(service.releaseClaim(entry));
        assertEquals(1, renewed.get());
        assertEquals(1, deleted.get());
        assertEquals(1, released.get());
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
