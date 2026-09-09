/**
 * Broker-agnostic messaging SPI.
 * <p>
 * Application code depends on this root package for the main facade and SPI:
 * <ul>
 *   <li>{@link Messaging} - the static entry point (producers and listeners);</li>
 *   <li>{@link dev.olegz.vf.messaging.MessageProducer} - sends records;</li>
 *   <li>{@link dev.olegz.vf.messaging.MessageListener} - consumes records (override
 *       {@code messageTtlMillis()} to skip stale messages);</li>
 *   <li>{@link dev.olegz.vf.messaging.Topics} - topic name constants.</li>
 * </ul>
 * Consumer registration options live in {@link dev.olegz.vf.messaging.consumer}; shared implementation
 * helpers live in {@link dev.olegz.vf.messaging.support}.
 * <p>
 * Concrete brokers are implementations of {@link MessageBroker}, discovered at runtime via
 * {@link java.util.ServiceLoader}, and selected explicitly with
 * {@link Messaging#broker(dev.olegz.vf.messaging.MessagingProvider)}. Kafka, Artemis, and SQS live in
 * sibling implementation packages without requiring application code to depend on their concrete classes.
 */
package dev.olegz.vf.messaging;
