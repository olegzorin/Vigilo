package dev.olegz.vf.messaging;

import dev.olegz.vf.messaging.consumer.ConsumerConfig;
import dev.olegz.vf.messaging.consumer.ConsumerMode;

/**
 * Broker-agnostic message transport SPI.
 * <p>
 * Each implementation adapts one concrete broker technology (Kafka, Artemis, SQS, etc.) to the common
 * producer/listener contract used by application code. Provider-specific semantics are exposed by capability
 * interfaces such as {@link KeyedMessageProducer} and {@link ScopedMessageBroker}. Implementations are discovered
 * at runtime through {@link java.util.ServiceLoader}; {@link Messaging#broker(MessagingProvider)} selects one by
 * comparing {@link #provider()} with the requested {@link MessagingProvider}.
 * <p>
 * Application code should depend on {@link Messaging} and the SPI types in this package, never on concrete broker
 * implementation classes.
 */
public interface MessageBroker {
    int DEFAULT_MAX_MESSAGE_SIZE = 1024 * 1024;
    int DEFAULT_MAX_POLL_INTERVAL = 60; // in seconds

    /**
     * Stable provider identity used by {@link Messaging#broker(MessagingProvider)} to choose the requested
     * implementation from the {@link java.util.ServiceLoader} results.
     */
    MessagingProvider provider();

    /**
     * Return a named producer. Producers are shared per name and created lazily.
     * @param name logical producer name, used to build a stable client id
     */
    MessageProducer getProducer(String name);

    /**
     * Create a single-thread, cluster-shared consumer reading data from the specified topic.
     * @param topic topic name
     * @param listener listener implementation
     * @param maxPollInterval maximum delay in seconds between polls before the consumer is considered failed
     * @param maxPollRecords maximum number of records to fetch in one poll
     */
    void setMessageListener(String topic, MessageListener listener, int maxPollInterval, int maxPollRecords);

    /**
     * Register cluster-shared concurrent processing of the topic, either preserving provider-native ordering
     * ({@link ConsumerMode#ORDERED}) or maximizing throughput without ordering ({@link ConsumerMode#CONCURRENT}).
     * @param config consumer tuning &mdash; mode, parallelism, and poll limits; see {@link ConsumerConfig}
     */
    void setMessageListeners(String topic, MessageListener listener, ConsumerConfig config);

    /** Maximum recommended message payload size in bytes for this broker. */
    int maxMessageSize();

    /** Close all producers. */
    void closeProducers();

    /** Close all consumers/listeners. */
    void close();
}
