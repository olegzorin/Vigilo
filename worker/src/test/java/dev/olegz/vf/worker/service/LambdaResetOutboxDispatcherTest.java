package dev.olegz.vf.worker.service;

import java.sql.Timestamp;
import java.util.ArrayDeque;
import java.util.Queue;

import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.domain.lambdarun.LambdaResetOutboxEntry;
import dev.olegz.vf.core.event.ResetEvent;
import dev.olegz.vf.core.service.lambda.LambdaResetOutboxService;
import dev.olegz.vf.messaging.ConfirmingKeyedMessageProducer;
import dev.olegz.vf.messaging.MessagingException;
import dev.olegz.vf.messaging.Topics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LambdaResetOutboxDispatcherTest {
    @BeforeAll
    static void oneTimeSetUp() {
        PropertyStore.start();
    }

    @Test
    void publishesWithLocationKeyAndCompletesClaims() {
        LambdaResetOutboxEntry first = entry(1, 21);
        LambdaResetOutboxEntry second = entry(2, 22);
        RecordingOutboxService outbox = new RecordingOutboxService(first, second);
        RecordingProducer producer = new RecordingProducer();

        int dispatched = new LambdaResetOutboxDispatcher(outbox, producer).dispatchPending();

        assertEquals(2, dispatched);
        assertEquals(2, producer.sendCount);
        assertEquals(2, outbox.completedCount);
        assertEquals(0, outbox.releasedCount);
        assertEquals(Topics.LAMBDA_RESET, producer.lastTopic);
        assertEquals(22, producer.lastKey);
        ResetEvent published = BytesMapper.readValue(producer.lastPayload, ResetEvent.class);
        assertEquals(second.eventId, published.eventId);
        assertEquals(second.eventTime, published.time);
        assertEquals(second.locationId, published.locationId);
        assertEquals(second.lambdaAssignmentId, published.lambdaAssignmentId);
        assertEquals(second.variableGeneration, published.variableGeneration);
    }

    @Test
    void releasesClaimWhenBrokerDoesNotAcknowledge() {
        LambdaResetOutboxEntry entry = entry(1, 21);
        RecordingOutboxService outbox = new RecordingOutboxService(entry);
        RecordingProducer producer = new RecordingProducer();
        producer.failure = new MessagingException("send failed");

        assertThrows(MessagingException.class,
            () -> new LambdaResetOutboxDispatcher(outbox, producer).dispatchPending());

        assertEquals(0, outbox.completedCount);
        assertEquals(1, outbox.releasedCount);
        assertSame(entry, outbox.lastReleased);
    }

    private static LambdaResetOutboxEntry entry(long id, int locationId) {
        LambdaResetOutboxEntry entry = new LambdaResetOutboxEntry(
            locationId, "event-" + id, 1000 + id, 100 + (int) id, 2000 + id, new Timestamp(1));
        entry.id = id;
        entry.claimId = "claim-" + id;
        return entry;
    }

    private static final class RecordingProducer implements ConfirmingKeyedMessageProducer {
        private int sendCount;
        private String lastTopic;
        private int lastKey;
        private byte[] lastPayload;
        private RuntimeException failure;

        @Override public void send(String topic, byte[] value) { throw new UnsupportedOperationException(); }
        @Override public void send(String topic, String key, byte[] value) { throw new UnsupportedOperationException(); }
        @Override public void sendAndAwait(String topic, byte[] value) { throw new UnsupportedOperationException(); }
        @Override public void sendAndAwait(String topic, String key, byte[] value) { throw new UnsupportedOperationException(); }

        @Override
        public void sendAndAwait(String topic, int key, byte[] value) {
            if (failure != null) throw failure;
            sendCount++;
            lastTopic = topic;
            lastKey = key;
            lastPayload = value;
        }
    }

    private static final class RecordingOutboxService implements LambdaResetOutboxService {
        private final Queue<LambdaResetOutboxEntry> entries = new ArrayDeque<>();
        private int completedCount;
        private int releasedCount;
        private LambdaResetOutboxEntry lastReleased;

        RecordingOutboxService(LambdaResetOutboxEntry... entries) {
            for (LambdaResetOutboxEntry entry : entries) this.entries.add(entry);
        }

        @Override public void enqueue(ResetEvent event) { throw new UnsupportedOperationException(); }
        @Override public LambdaResetOutboxEntry claimNext() { return entries.poll(); }
        @Override public boolean completeClaim(LambdaResetOutboxEntry entry) { completedCount++; return true; }

        @Override
        public boolean releaseClaim(LambdaResetOutboxEntry entry) {
            releasedCount++;
            lastReleased = entry;
            return true;
        }
    }
}
