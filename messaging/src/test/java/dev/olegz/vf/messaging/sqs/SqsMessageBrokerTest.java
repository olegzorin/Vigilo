package dev.olegz.vf.messaging.sqs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import dev.olegz.vf.aws.sqs.SqsSupport;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.messaging.MessageProducer;
import dev.olegz.vf.messaging.providers.sqs.SqsMessageBroker;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqsMessageBrokerTest {

    static {
        try {
            PropertyStore.set("vf.aws.local", "true");
            PropertyStore.set("vf.aws.local.root", Files.createTempDirectory("vf-sqs-message-test").toString());
            PropertyStore.set("vf.sqs.waitTimeSeconds", "1");
            PropertyStore.set("vf.sqs.visibilityTimeoutSeconds", "5");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void localSqsBrokerDeliversBytesAndAcknowledges() throws Exception {
        SqsMessageBroker broker = new SqsMessageBroker();
        String topic = "sqs-test-" + System.nanoTime();
        byte[] expected = new byte[] {0, 1, 2, -1};
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<byte[]> actual = new AtomicReference<>();

        try {
            broker.setMessageListener(topic, message -> {
                actual.set(message);
                received.countDown();
            }, 10, 1);

            MessageProducer producer = broker.getProducer("test");
            producer.send(topic, expected);

            assertTrue(received.await(5, TimeUnit.SECONDS), "message should be delivered through local SQS");
            assertArrayEquals(expected, actual.get());
        } finally {
            broker.close();
        }
    }

    @Test
    void localSqsBrokerDeliversRawExternalMessageBodies() throws Exception {
        SqsMessageBroker broker = new SqsMessageBroker();
        String topic = "sqs-raw-test-" + System.nanoTime();
        String expected = "{\"message\":\"raw lambda destination\"}";
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<byte[]> actual = new AtomicReference<>();

        try {
            broker.setMessageListener(topic, message -> {
                actual.set(message);
                received.countDown();
            }, 10, 1);

            SqsSupport.sendMessage(topic, expected, 0, 3600, 5);

            assertTrue(received.await(5, TimeUnit.SECONDS), "raw SQS body should be delivered");
            assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), actual.get());
        } finally {
            broker.close();
        }
    }
}
