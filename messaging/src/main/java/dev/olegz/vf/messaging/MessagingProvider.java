package dev.olegz.vf.messaging;

/**
 * Supported broker technologies for {@link Messaging#broker(MessagingProvider)}.
 * <p>
 * The enum is intentionally small and explicit: adding a broker means adding a provider value, a
 * {@link MessageBroker} implementation whose {@link MessageBroker#provider()} returns that value, and a
 * {@code META-INF/services/dev.olegz.vf.messaging.MessageBroker} entry. {@link #LOOPBACK} is the internal
 * exception used by {@link Messaging#disable()} and is not loaded from {@link java.util.ServiceLoader}.
 */
public enum MessagingProvider {
    /** Apache Kafka, used for the main application event streams. */
    KAFKA,

    /** AWS SQS, used for queue-based integrations such as Lambda destinations. */
    SQS,

    /** Apache ActiveMQ Artemis core client. */
    ARTEMIS,

    /** Internal in-process loopback broker used when external messaging is disabled. */
    LOOPBACK;
}
