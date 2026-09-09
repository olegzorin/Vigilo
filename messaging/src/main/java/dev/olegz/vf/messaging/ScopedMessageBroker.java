package dev.olegz.vf.messaging;

import dev.olegz.vf.messaging.consumer.ConsumerConfig;
import dev.olegz.vf.messaging.consumer.ConsumerGroupScope;

/**
 * Broker capability for listener registrations that use non-default sharing semantics.
 * <p>
 * Implement this only when the provider can honor {@link ConsumerGroupScope}.
 * Providers that only support a shared work queue should expose the
 * base {@link MessageBroker} methods instead.
 */
public interface ScopedMessageBroker extends MessageBroker {

    /**
     * Create a single-thread consumer reading data from the specified topic.
     * @param topic topic name
     * @param listener listener implementation
     * @param maxPollInterval maximum delay in seconds between polls before the consumer is considered failed
     * @param maxPollRecords maximum number of records to fetch in one poll
     * @param maxCommitInterval maximum delay in milliseconds between commits, {@code 0} for the default
     * @param consumerGroupScope how the record is shared; see {@link ConsumerGroupScope}
     */
    void setMessageListener(String topic, MessageListener listener, int maxPollInterval, int maxPollRecords,
        long maxCommitInterval, ConsumerGroupScope consumerGroupScope);

    /**
     * Register concurrent processing of the topic with explicit sharing semantics.
     * @param config consumer tuning; see {@link ConsumerConfig}
     * @param consumerGroupScope how the record is shared; see {@link ConsumerGroupScope}
     */
    void setMessageListeners(String topic, MessageListener listener, ConsumerConfig config, ConsumerGroupScope consumerGroupScope);
}
