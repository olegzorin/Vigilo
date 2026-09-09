/**
 * Apache ActiveMQ Artemis implementation of the messaging SPI, built on the Artemis <em>core</em> client.
 * <p>
 * {@link ArtemisMessageBroker} implements
 * {@link MessageBroker} and is the only public type here; everything else is an internal producer/consumer detail.
 * Application code never references this package directly - it goes through
 * {@link Messaging#broker(dev.olegz.vf.messaging.MessagingProvider)} with
 * {@link dev.olegz.vf.messaging.MessagingProvider#ARTEMIS}.
 * <br><br>
 * The SPI maps onto the Artemis address model: a topic is an <em>address</em>, a consumer group is a <em>queue</em>,
 * {@link ConsumerGroupScope#SHARED} uses an ANYCAST queue shared across the cluster and
 * {@link ConsumerGroupScope#PER_SERVER} a MULTICAST queue per server, and
 * {@link dev.olegz.vf.messaging.consumer.ConsumerMode#ORDERED} relies on message grouping (the producer's key) to
 * preserve per-key order across competing consumers. See {@link ArtemisMessageBroker}
 * for the full mapping.
 */
package dev.olegz.vf.messaging.providers.artemis;

import dev.olegz.vf.messaging.MessageBroker;
import dev.olegz.vf.messaging.Messaging;
import dev.olegz.vf.messaging.consumer.ConsumerGroupScope;
