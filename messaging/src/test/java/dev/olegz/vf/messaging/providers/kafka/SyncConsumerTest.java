package dev.olegz.vf.messaging.providers.kafka;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import dev.olegz.vf.messaging.MessageListener;
import dev.olegz.vf.messaging.RetryableMessageException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncConsumerTest {
    @Test
    void retryableFailureRewindsThePartitionAndDoesNotAdvancePastTheRecord() {
        TopicPartition partition = new TopicPartition("events", 0);
        MockConsumer<byte[], byte[]> kafka = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        kafka.assign(List.of(partition));
        kafka.updateBeginningOffsets(Map.of(partition, 0L));
        AtomicInteger calls = new AtomicInteger();
        MessageListener listener = message -> {
            calls.incrementAndGet();
            throw new RetryableMessageException("database unavailable", null);
        };
        SyncConsumer consumer = new SyncConsumer("events", kafka, listener, "test", 30_000L);
        ConsumerRecords<byte[], byte[]> records = new ConsumerRecords<>(Map.of(partition, List.of(
            new ConsumerRecord<>("events", 0, 0L, null, new byte[]{0}),
            new ConsumerRecord<>("events", 0, 1L, null, new byte[]{1}))));

        consumer.processRecords(records);

        assertEquals(1, calls.get());
        assertEquals(0L, kafka.position(partition));
    }
}
