/**
 * Kafka implementation of the messaging SPI.
 * <p>
 * {@link KafkaMessageBroker} implements
 * {@link MessageBroker} and is the only public type here; everything else is an
 * internal consumer/producer detail. Application code never references this package directly - it goes through
 * {@link Messaging}.
 * <br><br>
 * All consumers extend {@code TopicConsumer}, the abstract base bound to one topic that owns the poll/commit loop
 * of a consumer on its own thread and the start/close lifecycle the broker drives:
 * <ul>
 *   <li>{@code SyncConsumer} - single synchronous consumer; fast, batch, single-thread processing. The broker can
 *       register several of these on one topic (each gets its own partitions) when records with the same key must
 *       be processed on the same thread.</li>
 *   <li>{@code AsyncConsumer} - single consumer dispatching to its own thread pool; used when records can be
 *       processed in parallel and partitions committed independently as their records are acknowledged.</li>
 * </ul>
 */
package dev.olegz.vf.messaging.providers.kafka;

import dev.olegz.vf.messaging.MessageBroker;
import dev.olegz.vf.messaging.Messaging;
