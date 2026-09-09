package dev.olegz.vf.worker.service;

import java.sql.Timestamp;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.domain.lambdarun.LambdaRunCompletionOutboxEntry;
import dev.olegz.vf.core.domain.lambdarun.LambdaRunContext;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import dev.olegz.vf.core.service.lambda.LambdaRunCompletionOutboxService;
import dev.olegz.vf.messaging.ConfirmingMessageProducer;
import dev.olegz.vf.messaging.Topics;
import dev.olegz.vf.worker.scheduler.job.DispatchLambdaRunCompletionsJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class LambdaRunCompletionOutboxDispatcherTest {
    private LambdaRunCompletionOutboxDispatcher dispatcher;

    @BeforeAll
    static void oneTimeSetUp() {
        PropertyStore.start();
    }

    @AfterEach
    void tearDown() {
        if (dispatcher != null) dispatcher.shutdown();
    }

    @Test
    void publishesWithConfirmationAndCompletesClaim() {
        LambdaRunCompletionOutboxEntry first = entry(1);
        LambdaRunCompletionOutboxEntry second = entry(2);
        RecordingOutboxService outbox = new RecordingOutboxService(first, second);
        RecordingProducer producer = new RecordingProducer();
        dispatcher = dispatcher(outbox, producer);

        assertEquals(2, dispatcher.dispatchAvailableCompletions());

        assertEquals(2, producer.confirmedSends);
        assertEquals(0, producer.asyncSends);
        assertEquals(Topics.LAMBDA_RUN_COMPLETION, producer.topic);
        LambdaRunContext published = BytesMapper.readValue(producer.lastValue, LambdaRunContext.class);
        assertEquals(second.lambdaAssignmentId, published.lambdaAssignmentId);
        assertEquals(second.lane, published.lane);
        assertEquals(second.runId, published.requestId);
        assertEquals(2, outbox.completedCount);
        assertEquals(0, outbox.releasedCount);
    }

    @Test
    void failedPublicationReleasesClaimAndStopsBatch() {
        LambdaRunCompletionOutboxEntry first = entry(1);
        RecordingOutboxService outbox = new RecordingOutboxService(first, entry(2));
        RecordingProducer producer = new RecordingProducer();
        producer.failure = new RuntimeException("Kafka unavailable");
        dispatcher = dispatcher(outbox, producer);

        assertEquals(0, dispatcher.dispatchAvailableCompletions());

        assertEquals(0, outbox.completedCount);
        assertEquals(1, outbox.releasedCount);
        assertSame(first, outbox.lastReleased);
    }

    @Test
    void scheduledDispatcherRunsEverySecond() {
        RecordingOutboxService outbox = new RecordingOutboxService();
        dispatcher = dispatcher(outbox, new RecordingProducer());
        DispatchLambdaRunCompletionsJob job = new DispatchLambdaRunCompletionsJob(dispatcher);

        job.run();

        assertEquals("DispatchLambdaRunCompletions", job.name());
        assertEquals("* * * * * ?", job.cron());
    }

    private LambdaRunCompletionOutboxDispatcher dispatcher(
        RecordingOutboxService outbox,
        RecordingProducer producer)
    {
        return new LambdaRunCompletionOutboxDispatcher(
            outbox, producer, new ScheduledThreadPoolExecutor(1));
    }

    private static LambdaRunCompletionOutboxEntry entry(long runId) {
        LambdaRunContext context = new LambdaRunContext();
        context.lambdaAssignmentId = 17;
        context.lane = InvocationLane.DEFAULT;
        context.requestId = runId;
        LambdaRunCompletionOutboxEntry entry = new LambdaRunCompletionOutboxEntry(
            context, new Timestamp(System.currentTimeMillis()));
        entry.claimId = "claim-" + runId;
        return entry;
    }

    private static final class RecordingProducer implements ConfirmingMessageProducer {
        private int asyncSends;
        private int confirmedSends;
        private String topic;
        private byte[] lastValue;
        private RuntimeException failure;

        @Override
        public void send(String topic, byte[] value) {
            asyncSends++;
        }

        @Override
        public void sendAndAwait(String topic, byte[] value) {
            if (failure != null) throw failure;
            this.topic = topic;
            this.lastValue = value;
            confirmedSends++;
        }
    }

    private static final class RecordingOutboxService implements LambdaRunCompletionOutboxService {
        private final Queue<LambdaRunCompletionOutboxEntry> entries = new ArrayDeque<>();
        private int completedCount;
        private int releasedCount;
        private LambdaRunCompletionOutboxEntry lastReleased;

        RecordingOutboxService(LambdaRunCompletionOutboxEntry... entries) {
            for (LambdaRunCompletionOutboxEntry entry : entries) {
                this.entries.add(entry);
            }
        }

        @Override public void enqueue(LambdaRunContext context) { }
        @Override public LambdaRunCompletionOutboxEntry claimNextAvailable() { return entries.poll(); }
        @Override public boolean renewClaim(LambdaRunCompletionOutboxEntry entry) { return true; }

        @Override
        public boolean completeClaim(LambdaRunCompletionOutboxEntry entry) {
            completedCount++;
            return true;
        }

        @Override
        public boolean releaseClaim(LambdaRunCompletionOutboxEntry entry) {
            releasedCount++;
            lastReleased = entry;
            return true;
        }
    }
}
