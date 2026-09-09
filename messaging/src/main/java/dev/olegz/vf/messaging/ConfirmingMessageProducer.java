package dev.olegz.vf.messaging;

/**
 * Producer capability for callers that must know whether the broker accepted a record before
 * advancing durable local state.
 */
public interface ConfirmingMessageProducer extends MessageProducer {

    /**
     * Send a record and block until the broker acknowledges it or the send fails.
     *
     * @throws MessagingException when acknowledgement is not obtained
     */
    void sendAndAwait(String topic, byte[] value);
}
