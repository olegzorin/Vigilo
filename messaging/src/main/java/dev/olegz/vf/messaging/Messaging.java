package dev.olegz.vf.messaging;

import java.util.EnumMap;
import java.util.ServiceLoader;

import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.messaging.support.LoopbackMessageBroker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Broker registry and entry point for the broker-agnostic messaging layer.
 * <p>
 * Application code obtains brokers by {@link MessagingProvider} and then uses only the SPI types in this package
 * ({@link MessageBroker}, {@link MessageProducer}, {@link MessageListener}, {@link Topics}). It should not depend on
 * concrete implementation packages such as {@code kafka}, {@code artemis}, or {@code sqs}.
 * <p>
 * Brokers are discovered with {@link ServiceLoader}, selected by {@link MessageBroker#provider()}, and cached per
 * provider. This lets one JVM use more than one broker intentionally, for example Kafka for the main application
 * streams and SQS for AWS Lambda destination queues. When messaging is disabled, every provider resolves to the
 * in-memory loopback broker so tests and command-line tools do not open external broker connections.
 */
public final class Messaging {
    private static final Logger logger = LoggerFactory.getLogger(Messaging.class);

    // "vf.messaging.mock" swaps the default broker for an in-memory loopback (used in tests and local runs).
    private static volatile boolean disabled = PropertyStore.getBoolean("vf.messaging.mock", false);

    private static final EnumMap<MessagingProvider, MessageBroker> brokers = new EnumMap<>(MessagingProvider.class);

    private Messaging() {
    }

    /**
     * Return the broker implementation for {@code provider}, resolving it once through {@link ServiceLoader} and
     * caching it for subsequent calls.
     *
     * @param provider broker provider to resolve
     * @return the provider-specific broker, or the shared in-memory broker when messaging is disabled
     * @throws MessagingException if the provider is enabled but no matching {@link MessageBroker} implementation is
     *                            present on the classpath
     */
    public static MessageBroker broker(MessagingProvider provider) {
        if (provider == MessagingProvider.LOOPBACK) return LoopbackMessageBroker.INSTANCE;
        MessageBroker b = brokers.get(provider);
        if (b == null) {
            synchronized (brokers) {
                b = brokers.get(provider);
                if (b == null) {
                    b = disabled ? LoopbackMessageBroker.INSTANCE : loadBroker(provider);
                    brokers.put(provider, b);
                }
            }
        }
        return b;
    }

    /**
     * Return the broker as a required capability, failing fast when the selected provider cannot honor that contract.
     */
    public static <T> T broker(MessagingProvider provider, Class<T> capability) {
        MessageBroker broker = broker(provider);
        if (capability.isInstance(broker)) return capability.cast(broker);
        throw new UnsupportedOperationException(provider + " broker does not support " + capability.getSimpleName());
    }

    /**
     * Return a named producer as a required capability, failing fast when the selected provider cannot honor that
     * producer contract.
     */
    public static <T> T producer(MessagingProvider provider, String name, Class<T> capability) {
        MessageProducer producer = broker(provider).getProducer(name);
        if (capability.isInstance(producer)) return capability.cast(producer);
        throw new UnsupportedOperationException(provider + " producer does not support " + capability.getSimpleName());
    }

    private static MessageBroker loadBroker(MessagingProvider provider) {
        var brokers = ServiceLoader.load(MessageBroker.class, MessageBroker.class.getClassLoader());
        for (MessageBroker b : brokers) {
            if (provider == b.provider()) {
                logger.info("Message broker provider: {}", provider);
                return b;
            }
        }
        throw new MessagingException("No MessageBroker provider " + provider  + " found on the classpath");
    }

    /**
     * Disable external brokers for this JVM.
     * <p>
     * After this call, every {@link #broker(MessagingProvider)} lookup returns the shared in-memory loopback broker,
     * which delivers produced records to listeners registered in the same JVM and never reaches Kafka, SQS, or
     * Artemis. This is intended for tests and one-shot command-line tools.
     */
    public static void disable() {
        disabled = true;
        for (MessagingProvider provider : MessagingProvider.values()) {
            brokers.put(provider, LoopbackMessageBroker.INSTANCE);
        }
    }
}
