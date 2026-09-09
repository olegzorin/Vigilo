package dev.olegz.vf.messaging;

import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.messaging.consumer.AckStatus;

/**
 * Common interface for processing a single data streaming message
 */
public interface MessageListener {
    /** Extra slack added to a message TTL to tolerate clock differences between hosts (ms). */
    long EXPIRY_SLACK_MS = PropertyStore.getLong("vf.mq.maxTimeDifference", 10L) * 1000L;

    /**
     * Process message
     */
    void onMessage(byte[] message);

    /**
     * Process a message with acknowledgement control. The default delegates to
     * {@link #onMessage(byte[])} and acknowledges only after it returns successfully.
     */
    default void onMessage(byte[] message, AckStatus ack) {
        onMessage(message);
        ack.acknowledge();
    }

    /**
     * Check if all processed records can be committed. Used to control commits frequence.
     * @param time current time
     * @return true to start commit
     */
    default boolean commit(long time) {
        return true;
    }

    /**
     * Time-to-live for consumed messages, in milliseconds. Override to make this listener ignore stale messages.
     * @return the TTL in ms; {@code 0} (default) means messages never expire
     */
    default long messageTtlMillis() {
        return 0L;
    }

    /**
     * Check if the message is too old to process and must be ignored. By default a message is expired once it is
     * older than {@link #messageTtlMillis()} (plus {@link #EXPIRY_SLACK_MS}); a listener may override either method.
     * @param time message timestamp
     * @return true, if the message expired
     */
    default boolean expired(long time) {
        long ttl = messageTtlMillis();
        return ttl > 0L && time + ttl + EXPIRY_SLACK_MS < System.currentTimeMillis();
    }
}
