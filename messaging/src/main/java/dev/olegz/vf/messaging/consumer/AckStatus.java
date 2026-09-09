package dev.olegz.vf.messaging.consumer;

/**
 * Acknowledgement status of a single message, used for asynchronous processing.
 * <p>
 * A listener that processes a message on another thread calls {@link #acknowledge()} when finished; the broker uses
 * {@link #isAcknowledged()} to decide when the message can be committed. It is broker-agnostic on purpose: any
 * positioning information (such as a Kafka offset) is tracked by the broker implementation, not here.
 */
public class AckStatus {
    private volatile boolean acknowledged;

    /** Mark the message as processed so the broker may commit it. */
    public void acknowledge() {
        acknowledged = true;
    }

    /** @return true once {@link #acknowledge()} has been called. */
    public boolean isAcknowledged() {
        return acknowledged;
    }
}
