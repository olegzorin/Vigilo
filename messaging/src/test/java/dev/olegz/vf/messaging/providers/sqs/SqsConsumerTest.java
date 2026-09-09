package dev.olegz.vf.messaging.providers.sqs;

import dev.olegz.vf.messaging.MessageListener;
import dev.olegz.vf.messaging.consumer.AckStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SqsConsumerTest {

    @Test
    void acknowledgementAwareFailureIsLeftForRedelivery() {
        MessageListener listener = new MessageListener() {
            @Override public void onMessage(byte[] message) {
            }

            @Override public void onMessage(byte[] message, AckStatus ack) {
                throw new RuntimeException("temporary failure");
            }
        };
        AckStatus ack = new AckStatus();

        consumer(listener).deliver(new byte[]{1}, ack);

        assertFalse(ack.isAcknowledged());
    }

    @Test
    void defaultListenerFailureIsLeftForRedelivery() {
        MessageListener listener = message -> {
            throw new RuntimeException("temporary failure");
        };
        AckStatus ack = new AckStatus();

        consumer(listener).deliver(new byte[]{1}, ack);

        assertFalse(ack.isAcknowledged());
    }

    private static SqsConsumer consumer(MessageListener listener) {
        return new SqsConsumer("topic", "queue", listener, "test", 1, 1, 30, 1);
    }
}
