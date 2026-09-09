package dev.olegz.vf.messaging;

/**
 * Producer capability for callers that require both broker-confirmed publication and stable keyed routing.
 */
public interface ConfirmingKeyedMessageProducer extends ConfirmingMessageProducer, KeyedMessageProducer {
    void sendAndAwait(String topic, String key, byte[] value);

    void sendAndAwait(String topic, int key, byte[] value);
}
