package dev.olegz.vf.core.service.lambda;

import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.dao.LambdaRunCompletionOutboxDao;
import dev.olegz.vf.core.domain.lambdarun.LambdaRunCompletionOutboxEntry;
import dev.olegz.vf.core.domain.lambdarun.LambdaRunContext;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

class LambdaRunCompletionOutboxServiceImplTest {
    @BeforeAll
    static void oneTimeSetUp() {
        PropertyStore.start();
    }

    @Test
    void enqueueUsesRequiresNewAndPersistsCompletionIdentity() throws Exception {
        AtomicReference<LambdaRunCompletionOutboxEntry> inserted = new AtomicReference<>();
        AtomicInteger insertCount = new AtomicInteger();
        LambdaRunCompletionOutboxDao dao = proxy((_, method, args) -> {
            if (method.getName().equals("insert")) {
                inserted.set((LambdaRunCompletionOutboxEntry) args[0]);
                insertCount.incrementAndGet();
                return null;
            }
            return defaultValue(method.getReturnType());
        });
        LambdaRunCompletionOutboxService service = new LambdaRunCompletionOutboxServiceImpl(dao);
        LambdaRunContext context = context();

        service.enqueue(context);
        service.enqueue(context);

        LambdaRunCompletionOutboxEntry entry = inserted.get();
        assertEquals(context.lambdaAssignmentId, entry.lambdaAssignmentId);
        assertEquals(context.lane, entry.lane);
        assertEquals(context.requestId, entry.runId);
        assertNotNull(entry.createdAt);
        assertEquals(2, insertCount.get());

        Transactional transaction = LambdaRunCompletionOutboxServiceImpl.class
            .getMethod("enqueue", LambdaRunContext.class)
            .getAnnotation(Transactional.class);
        assertEquals(Propagation.REQUIRES_NEW, transaction.propagation());
    }

    @Test
    void claimsAvailableEntryWithLeaseAndFencesTerminalUpdates() {
        LambdaRunCompletionOutboxEntry available = new LambdaRunCompletionOutboxEntry(
            context(), new Timestamp(System.currentTimeMillis() - 1));
        AtomicReference<LambdaRunCompletionOutboxEntry> claimed = new AtomicReference<>();
        AtomicInteger renewed = new AtomicInteger();
        AtomicInteger deleted = new AtomicInteger();
        AtomicInteger released = new AtomicInteger();
        LambdaRunCompletionOutboxDao dao = proxy((_, method, args) -> switch (method.getName()) {
            case "claimNextAvailable" -> {
                available.claimId = (String) args[1];
                available.claimUntil = (Timestamp) args[2];
                claimed.set(available);
                yield available;
            }
            case "renewClaim" -> renewed.incrementAndGet() > 0;
            case "deleteClaimed" -> deleted.incrementAndGet() > 0;
            case "releaseClaim" -> released.incrementAndGet() > 0;
            default -> defaultValue(method.getReturnType());
        });
        LambdaRunCompletionOutboxService service = new LambdaRunCompletionOutboxServiceImpl(dao);
        long before = System.currentTimeMillis();

        LambdaRunCompletionOutboxEntry entry = service.claimNextAvailable();

        assertSame(available, entry);
        assertSame(entry, claimed.get());
        assertNotNull(entry.claimId);
        assertTrue(entry.claimUntil.getTime() > before);
        assertTrue(service.renewClaim(entry));
        assertTrue(service.completeClaim(entry));
        assertTrue(service.releaseClaim(entry));
        assertEquals(1, renewed.get());
        assertEquals(1, deleted.get());
        assertEquals(1, released.get());
    }

    private static LambdaRunContext context() {
        LambdaRunContext context = new LambdaRunContext();
        context.lambdaAssignmentId = 17;
        context.lane = InvocationLane.DEFAULT;
        context.requestId = 1_750_000_000_000L;
        context.lambdaId = 4;
        context.lambdaVersionId = 8;
        return context;
    }

    private static LambdaRunCompletionOutboxDao proxy(java.lang.reflect.InvocationHandler handler) {
        return (LambdaRunCompletionOutboxDao) Proxy.newProxyInstance(
            LambdaRunCompletionOutboxDao.class.getClassLoader(),
            new Class<?>[]{LambdaRunCompletionOutboxDao.class},
            handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
