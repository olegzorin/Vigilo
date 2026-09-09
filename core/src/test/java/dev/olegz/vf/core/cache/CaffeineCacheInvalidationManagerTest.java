package dev.olegz.vf.core.cache;

import dev.olegz.vf.registry.cache.CacheNames;
import java.util.ArrayList;
import java.util.List;

import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.core.domain.cache.CacheInvalidationOutboxEntry;
import dev.olegz.vf.core.service.cache.CacheInvalidationOutboxService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

class CaffeineCacheInvalidationManagerTest {

    @AfterEach
    void cleanUpTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void enqueuesBeforeCommitAndDefersLocalEvictionUntilCommit() {
        RecordingOutboxService outbox = new RecordingOutboxService();
        Cache cache = new CaffeineCacheInvalidationManager(outbox)
            .getCache(CacheNames.TRIGGER_LOCATION_METADATA);
        assertNotNull(cache);
        cache.put(42, "old");

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        cache.evict(42);

        assertEquals("old", cache.get(42, String.class));
        CacheInvalidationEvent event = BytesMapper.readValue(outbox.payloads.getFirst(), CacheInvalidationEvent.class);
        assertEquals(CacheNames.TRIGGER_LOCATION_METADATA, event.cache);
        assertEquals(42, event.key);

        List<TransactionSynchronization> synchronizations =
            new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        for (TransactionSynchronization synchronization : synchronizations) synchronization.afterCommit();
        for (TransactionSynchronization synchronization : synchronizations) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);

        assertNull(cache.get(42));
    }

    @Test
    void rollbackKeepsLocalEntry() {
        RecordingOutboxService outbox = new RecordingOutboxService();
        Cache cache = new CaffeineCacheInvalidationManager(outbox)
            .getCache(CacheNames.TRIGGER_LOCATION_METADATA);
        assertNotNull(cache);
        cache.put(42, "old");

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        cache.evict(42);

        List<TransactionSynchronization> synchronizations =
            new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        for (TransactionSynchronization synchronization : synchronizations) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);

        assertEquals(1, outbox.payloads.size());
        assertEquals("old", cache.get(42, String.class));
    }

    private static class RecordingOutboxService implements CacheInvalidationOutboxService {
        private final List<byte[]> payloads = new ArrayList<>();

        @Override
        public void enqueue(byte[] payload) {
            payloads.add(payload);
        }

        @Override
        public CacheInvalidationOutboxEntry claimNext() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean completeClaim(CacheInvalidationOutboxEntry entry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean releaseClaim(CacheInvalidationOutboxEntry entry) {
            throw new UnsupportedOperationException();
        }
    }
}
