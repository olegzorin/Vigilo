package dev.olegz.vf.messaging;

import java.util.ServiceLoader;

import dev.olegz.vf.messaging.consumer.ConsumerConfig;
import dev.olegz.vf.messaging.providers.kafka.KafkaMessageBroker;
import dev.olegz.vf.messaging.providers.sqs.SqsMessageBroker;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MessagingTest {

    /** The Kafka provider must be discoverable through the SPI so {@link Messaging} can resolve a broker. */
    @Test
    @Order(1)
    void kafkaBrokerIsRegisteredAsServiceProvider() {
        boolean found = false;
        for (MessageBroker broker : ServiceLoader.load(MessageBroker.class)) {
            if (broker instanceof KafkaMessageBroker) {
                found = true;
                break;
            }
        }
        assertTrue(found, "KafkaMessageBroker must be registered in META-INF/services");
    }

    @Test
    @Order(2)
    void sqsBrokerIsRegisteredAsServiceProvider() {
        boolean found = false;
        for (MessageBroker broker : ServiceLoader.load(MessageBroker.class)) {
            if (broker instanceof SqsMessageBroker) {
                found = true;
                break;
            }
        }
        assertTrue(found, "SqsMessageBroker must be registered in META-INF/services");
    }

    @Test
    @Order(3)
    void explicitBrokerCanBeResolvedByProvider() {
        MessageBroker sqs = Messaging.broker(MessagingProvider.SQS);

        assertNotNull(sqs);
        assertSame(sqs, Messaging.broker(MessagingProvider.SQS), "explicit brokers should be cached by provider");
    }

    /**
     * The disabled path must be a safe no-op: producers swallow records and listener registration does nothing,
     * so callers keep working without a broker. (disable() is global and irreversible for this JVM.)
     */
    @Test
    @Order(5)
    void disabledBrokerIsSafeNoOp() {
        Messaging.disable();

        MessageBroker broker = Messaging.broker(MessagingProvider.KAFKA);
        assertSame(broker, Messaging.broker(MessagingProvider.SQS),
            "disable() should route every provider to the same in-memory broker");

        MessageProducer producer = broker.getProducer("test");
        assertNotNull(producer, "a disabled broker must still return a (no-op) producer");

        // none of these may throw
        producer.send("topic", new byte[] {1});
        ConfirmingMessageProducer confirmingProducer = assertInstanceOf(ConfirmingMessageProducer.class, producer);
        confirmingProducer.sendAndAwait("topic", new byte[] {1});
        KeyedMessageProducer keyedProducer = assertInstanceOf(KeyedMessageProducer.class, producer);
        keyedProducer.send("topic", "key", new byte[] {1});
        keyedProducer.send("topic", 7, new byte[] {1});
        ConfirmingKeyedMessageProducer confirmingKeyedProducer =
            assertInstanceOf(ConfirmingKeyedMessageProducer.class, producer);
        confirmingKeyedProducer.sendAndAwait("topic", "key", new byte[] {1});
        confirmingKeyedProducer.sendAndAwait("topic", 7, new byte[] {1});
        broker.setMessageListener("topic", NOOP, 10, 1);
        broker.setMessageListeners("topic", NOOP, ConsumerConfig.ordered(1).withMaxPollInterval(10));
        broker.setMessageListeners("topic", NOOP, ConsumerConfig.concurrent(1).withMaxPollInterval(10));
        broker.closeProducers();
        broker.close();
    }

    @Test
    @Order(4)
    void sqsDoesNotAdvertiseUnsupportedCapabilities() {
        SqsMessageBroker broker = new SqsMessageBroker();

        assertFalse(broker instanceof ScopedMessageBroker, "SQS must not advertise scoped listener support");
        assertFalse(broker.getProducer("test") instanceof KeyedMessageProducer, "SQS must not advertise keyed sends");
    }

    private static final MessageListener NOOP = _ -> { };

}
