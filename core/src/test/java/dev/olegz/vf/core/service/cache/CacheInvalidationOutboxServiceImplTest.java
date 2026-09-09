package dev.olegz.vf.core.service.cache;

import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.util.concurrent.atomic.AtomicReference;

import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.dao.CacheInvalidationOutboxDao;
import dev.olegz.vf.core.domain.cache.CacheInvalidationOutboxEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CacheInvalidationOutboxServiceImplTest {

    @BeforeAll
    static void oneTimeSetUp() {
        PropertyStore.start();
    }

    @Test
    void enqueuesPayloadAndClaimsAvailableEntry() {
        byte[] payload = {1, 2, 3};
        CacheInvalidationOutboxEntry available = new CacheInvalidationOutboxEntry(payload, new Timestamp(1));
        AtomicReference<CacheInvalidationOutboxEntry> inserted = new AtomicReference<>();
        AtomicReference<CacheInvalidationOutboxEntry> claimed = new AtomicReference<>();

        CacheInvalidationOutboxDao dao = (CacheInvalidationOutboxDao) Proxy.newProxyInstance(
            CacheInvalidationOutboxDao.class.getClassLoader(),
            new Class<?>[]{CacheInvalidationOutboxDao.class},
            (_, method, args) -> switch (method.getName()) {
                case "insert" -> {
                    inserted.set((CacheInvalidationOutboxEntry) args[0]);
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

        CacheInvalidationOutboxService service = new CacheInvalidationOutboxServiceImpl(dao);
        service.enqueue(payload);
        CacheInvalidationOutboxEntry entry = service.claimNext();

        assertSame(payload, inserted.get().payload);
        assertNotNull(inserted.get().createdAt);
        assertSame(available, entry);
        assertSame(available, claimed.get());
        assertNotNull(entry.claimId);
        assertNotNull(entry.claimUntil);
        assertTrue(entry.claimUntil.after(new Timestamp(System.currentTimeMillis())));
    }

    @Test
    void returnsNullWhenNoEntryIsAvailable() {
        CacheInvalidationOutboxDao dao = (CacheInvalidationOutboxDao) Proxy.newProxyInstance(
            CacheInvalidationOutboxDao.class.getClassLoader(),
            new Class<?>[]{CacheInvalidationOutboxDao.class},
            (_, method, _) -> {
                if (method.getName().equals("claimNextAvailable")) return null;
                throw new UnsupportedOperationException(method.getName());
            });

        assertNull(new CacheInvalidationOutboxServiceImpl(dao).claimNext());
    }
}
