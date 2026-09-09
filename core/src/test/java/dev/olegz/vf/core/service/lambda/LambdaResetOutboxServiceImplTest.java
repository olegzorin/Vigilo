package dev.olegz.vf.core.service.lambda;

import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.util.concurrent.atomic.AtomicReference;

import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.dao.LambdaResetOutboxDao;
import dev.olegz.vf.core.domain.lambdarun.LambdaResetOutboxEntry;
import dev.olegz.vf.core.event.ResetEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LambdaResetOutboxServiceImplTest {
    @BeforeAll
    static void oneTimeSetUp() {
        PropertyStore.start();
    }

    @Test
    void enqueuesEventFieldsAndClaimsAvailableEntry() {
        ResetEvent event = resetEvent();
        LambdaResetOutboxEntry available = new LambdaResetOutboxEntry(
            21, "event-1", 1234, 101, 1234, new Timestamp(1));
        AtomicReference<LambdaResetOutboxEntry> inserted = new AtomicReference<>();
        AtomicReference<LambdaResetOutboxEntry> claimed = new AtomicReference<>();
        LambdaResetOutboxDao dao = dao((_, method, args) -> switch (method.getName()) {
            case "insert" -> {
                inserted.set((LambdaResetOutboxEntry) args[0]);
                yield null;
            }
            case "claimNextAvailable" -> {
                available.claimId = (String) args[1];
                available.claimUntil = (Timestamp) args[2];
                claimed.set(available);
                yield available;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
        LambdaResetOutboxService service = new LambdaResetOutboxServiceImpl(dao);

        service.enqueue(event);
        LambdaResetOutboxEntry entry = service.claimNext();

        assertEquals(21, inserted.get().locationId);
        assertEquals("event-1", inserted.get().eventId);
        assertEquals(1234, inserted.get().eventTime);
        assertEquals(101, inserted.get().lambdaAssignmentId);
        assertEquals(1234, inserted.get().variableGeneration);
        assertNotNull(inserted.get().createdAt);
        assertEquals(available, entry);
        assertEquals(available, claimed.get());
        assertNotNull(entry.claimId);
        assertTrue(entry.claimUntil.after(new Timestamp(System.currentTimeMillis())));
    }

    @Test
    void returnsNullWhenNoEntryIsAvailable() {
        LambdaResetOutboxDao dao = dao((_, method, _) -> {
            if (method.getName().equals("claimNextAvailable")) return null;
            throw new UnsupportedOperationException(method.getName());
        });

        assertNull(new LambdaResetOutboxServiceImpl(dao).claimNext());
    }

    private static LambdaResetOutboxDao dao(java.lang.reflect.InvocationHandler handler) {
        return (LambdaResetOutboxDao) Proxy.newProxyInstance(
            LambdaResetOutboxDao.class.getClassLoader(), new Class<?>[]{LambdaResetOutboxDao.class}, handler);
    }

    private static ResetEvent resetEvent() {
        ResetEvent event = new ResetEvent(21, 101);
        event.eventId = "event-1";
        event.time = 1234;
        event.variableGeneration = 1234;
        return event;
    }
}
