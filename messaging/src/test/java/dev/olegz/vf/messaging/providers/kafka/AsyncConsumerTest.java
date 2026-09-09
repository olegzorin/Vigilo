package dev.olegz.vf.messaging.providers.kafka;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import dev.olegz.vf.messaging.MessageListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("deprecation")
class AsyncConsumerTest {
    private static final String TOPIC = "test-topic";

    @Test
    void keepsPollingWhileWorkerPoolIsSaturated() throws Exception {
        TopicPartition partition = new TopicPartition(TOPIC, 0);
        TrackingMockConsumer kafkaConsumer = new TrackingMockConsumer();
        kafkaConsumer.assign(List.of(partition));
        kafkaConsumer.updateBeginningOffsets(Map.of(partition, 0L));
        kafkaConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 0L, null, new byte[]{0}));
        kafkaConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 1L, null, new byte[]{1}));

        AtomicInteger calls = new AtomicInteger();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch firstFinished = new CountDownLatch(1);
        CountDownLatch secondFinished = new CountDownLatch(1);
        MessageListener listener = message -> {
            if (calls.incrementAndGet() == 1) {
                firstStarted.countDown();
                await(releaseFirst);
                firstFinished.countDown();
            } else {
                secondFinished.countDown();
            }
        };

        AsyncConsumer consumer = new AsyncConsumer(TOPIC, kafkaConsumer, listener, "test", 1, 1000L);
        try {
            consumer.poll();

            assertTrue(firstStarted.await(1, TimeUnit.SECONDS));
            assertEquals(2, kafkaConsumer.polls.get(), "saturation must trigger a heartbeat poll");
            assertEquals(1, kafkaConsumer.pauses.get());
            assertEquals(1, kafkaConsumer.resumes.get());
            assertTrue(kafkaConsumer.paused().isEmpty());

            releaseFirst.countDown();
            assertTrue(firstFinished.await(1, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while ((secondFinished.getCount() > 0) && (System.nanoTime() < deadline)) consumer.poll();

            assertTrue(secondFinished.await(100, TimeUnit.MILLISECONDS));
            assertEquals(2, calls.get());
        } finally {
            releaseFirst.countDown();
            consumer.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static final class TrackingMockConsumer extends MockConsumer<byte[], byte[]> {
        private final AtomicInteger polls = new AtomicInteger();
        private final AtomicInteger pauses = new AtomicInteger();
        private final AtomicInteger resumes = new AtomicInteger();

        private TrackingMockConsumer() {
            super(OffsetResetStrategy.EARLIEST);
        }

        @Override
        public synchronized ConsumerRecords<byte[], byte[]> poll(Duration timeout) {
            polls.incrementAndGet();
            return super.poll(timeout);
        }

        @Override
        public synchronized void pause(java.util.Collection<TopicPartition> partitions) {
            pauses.incrementAndGet();
            super.pause(partitions);
        }

        @Override
        public synchronized void resume(java.util.Collection<TopicPartition> partitions) {
            resumes.incrementAndGet();
            super.resume(partitions);
        }
    }
}
