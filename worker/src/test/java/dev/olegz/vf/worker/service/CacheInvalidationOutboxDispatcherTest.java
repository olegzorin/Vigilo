package dev.olegz.vf.worker.service;

import java.sql.Timestamp;
import java.util.ArrayDeque;
import java.util.Queue;

import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.domain.cache.CacheInvalidationOutboxEntry;
import dev.olegz.vf.core.service.cache.CacheInvalidationOutboxService;
import dev.olegz.vf.messaging.ConfirmingMessageProducer;
import dev.olegz.vf.messaging.MessagingException;
import dev.olegz.vf.messaging.Topics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CacheInvalidationOutboxDispatcherTest {

    @BeforeAll
    static void oneTimeSetUp() {
        PropertyStore.start();
    }

    @Test
    void publishesAndCompletesEveryClaimedEntry() {
        CacheInvalidationOutboxEntry first = entry(1);
        CacheInvalidationOutboxEntry second = entry(2);
        RecordingOutboxService outbox = new RecordingOutboxService(first, second);
        RecordingProducer producer = new RecordingProducer();

        int dispatched = new CacheInvalidationOutboxDispatcher(outbox, producer).dispatchPending();

        assertEquals(2, dispatched);
        assertEquals(2, producer.sendCount);
        assertEquals(2, outbox.completedCount);
        assertEquals(0, outbox.releasedCount);
        assertEquals(Topics.CACHE_INVALIDATION, producer.lastTopic);
        assertSame(second.payload, producer.lastPayload);
    }

    @Test
    void releasesClaimWhenBrokerDoesNotAcknowledge() {
        CacheInvalidationOutboxEntry entry = entry(1);
        RecordingOutboxService outbox = new RecordingOutboxService(entry);
        RecordingProducer producer = new RecordingProducer();
        producer.failure = new MessagingException("send failed");

        assertThrows(MessagingException.class,
            () -> new CacheInvalidationOutboxDispatcher(outbox, producer).dispatchPending());

        assertEquals(0, outbox.completedCount);
        assertEquals(1, outbox.releasedCount);
        assertSame(entry, outbox.lastReleased);
    }

    private static CacheInvalidationOutboxEntry entry(long id) {
        CacheInvalidationOutboxEntry entry =
            new CacheInvalidationOutboxEntry(new byte[]{(byte) id}, new Timestamp(1));
        entry.id = id;
        entry.claimId = "claim-" + id;
        return entry;
    }

    private static class RecordingProducer implements ConfirmingMessageProducer {
        private int sendCount;
        private String lastTopic;
        private byte[] lastPayload;
        private RuntimeException failure;

        @Override
        public void send(String topic, byte[] value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void sendAndAwait(String topic, byte[] value) {
            if (failure != null) throw failure;
            sendCount++;
            lastTopic = topic;
            lastPayload = value;
        }
    }

    private static class RecordingOutboxService implements CacheInvalidationOutboxService {
        private final Queue<CacheInvalidationOutboxEntry> entries = new ArrayDeque<>();
        private int completedCount;
        private int releasedCount;
        private CacheInvalidationOutboxEntry lastReleased;

        RecordingOutboxService(CacheInvalidationOutboxEntry... entries) {
            for (CacheInvalidationOutboxEntry entry : entries) this.entries.add(entry);
        }

        @Override
        public void enqueue(byte[] payload) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CacheInvalidationOutboxEntry claimNext() {
            return entries.poll();
        }

        @Override
        public boolean completeClaim(CacheInvalidationOutboxEntry entry) {
            completedCount++;
            return true;
        }

        @Override
        public boolean releaseClaim(CacheInvalidationOutboxEntry entry) {
            releasedCount++;
            lastReleased = entry;
            return true;
        }
    }
}
