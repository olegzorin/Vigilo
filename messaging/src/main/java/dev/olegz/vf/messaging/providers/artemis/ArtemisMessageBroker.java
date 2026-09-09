package dev.olegz.vf.messaging.providers.artemis;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import dev.olegz.vf.common.RuntimeIdentity;
import dev.olegz.vf.common.ShutdownManager;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.messaging.*;
import dev.olegz.vf.messaging.consumer.ConsumerConfig;
import dev.olegz.vf.messaging.consumer.ConsumerGroupScope;
import dev.olegz.vf.messaging.consumer.ConsumerMode;
import dev.olegz.vf.messaging.providers.kafka.KafkaMessageBroker;
import dev.olegz.vf.messaging.support.MessagingUtils;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Apache ActiveMQ Artemis implementation of {@link MessageBroker}, built on the Artemis <em>core</em> client.
 * <p>
 * The provider identity is {@link MessagingProvider#ARTEMIS}. The broker is discovered via
 * {@link java.util.ServiceLoader} and should be obtained through {@link Messaging#broker(MessagingProvider)}, not by
 * constructing this class directly.
 * <p>
 * SPI concepts map onto the Artemis address model as follows:
 * <ul>
 *   <li>A <b>topic</b> is an Artemis <b>address</b>; the listener's consumer group (see
 *       {@link KafkaMessageBroker}'s group ids) becomes the <b>queue</b> name.</li>
 *   <li>{@link ConsumerGroupScope#SHARED} &rarr; an <b>ANYCAST</b> address with one durable queue named after the topic; every
 *       server's consumers compete on it, so each record is handled once by the cluster.</li>
 *   <li>{@link ConsumerGroupScope#PER_SERVER} &rarr; a <b>MULTICAST</b> address with a non-durable queue per server (name
 *       prefixed with the server id), so every server receives its own copy of every record.</li>
 *   <li>{@link ConsumerGroupScope#PER_LISTENER} &rarr; a <b>MULTICAST</b> address with a non-durable queue per listener
 *       registration (name prefixed with the server id and made unique per registration), so every listener &mdash;
 *       even several on one server &mdash; receives its own copy of every record.</li>
 *   <li>{@link ConsumerMode#ORDERED} relies on message grouping: a keyed {@link MessageProducer#send} stamps the
 *       record's group id, and Artemis pins each group to a single consumer, preserving per-key order across the
 *       {@code poolSize} competing consumers. Records sent without a key are spread round-robin (no order), exactly
 *       as on Kafka where an unkeyed record lands on an arbitrary partition.</li>
 *   <li>{@link ConsumerMode#CONCURRENT} is a single consumer feeding a {@code poolSize}-thread pool (see
 *       {@link ArtemisPoolConsumer}): every record arrives at the one consumer and fans out to the pool, so any
 *       producer-stamped group id is ignored and order is not preserved &mdash; the mode therefore behaves the same
 *       with or without keys, mirroring Kafka's thread-pool consumer.</li>
 * </ul>
 * Unlike Kafka's single monotonic offset per partition, Artemis acknowledges each message, so a consumer simply
 * acks a record once its listener returns and flushes those acks on the {@code maxCommitInterval} cadence; no
 * contiguous-offset bookkeeping is needed.
 */
public class ArtemisMessageBroker implements ScopedMessageBroker {
    private static final Logger logger = LoggerFactory.getLogger(ArtemisMessageBroker.class);

    // Manual-ack sessions flush acknowledgements on commit(); this only bounds Artemis' own auto-flush, which we
    // pre-empt, so a generous value is fine.
    private static final int ACK_BATCH_SIZE = 1 << 20;

    private final ConcurrentHashMap<String, ArtemisMessageProducer> producers = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<ArtemisConsumer> consumers = new ConcurrentLinkedQueue<>();
    private final AtomicInteger clientIndex = new AtomicInteger(0);

    private final int maxMessageSize = PropertyStore.getInt("vf.artemis.maxSize", MessageBroker.DEFAULT_MAX_MESSAGE_SIZE);
    private final int warnMessageSize = PropertyStore.getInt("vf.artemis.warnSize", maxMessageSize * 3 / 4);

    // Shared transport, created lazily on first producer/consumer so construction (and ServiceLoader discovery)
    // never touches the network.
    private volatile ClientSessionFactory factory;
    private ServerLocator locator;

    /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
    public ArtemisMessageBroker() {
        // Close consumers and the transport on application shutdown without 'common' depending on this module.
        ShutdownManager.register(this::close);
    }

    @Override
    public MessagingProvider provider() {
        return MessagingProvider.ARTEMIS;
    }

    @Override
    public int maxMessageSize() {
        return maxMessageSize;
    }

    private static String url() {
        String url = PropertyStore.getString("vf.artemis.url");
        if (url == null || url.isBlank()) throw new MessagingException("Artemis url not configured");
        return url;
    }

    private static String user() {
        return PropertyStore.getString("vf.artemis.user");
    }

    private static String password() {
        return PropertyStore.getString("vf.artemis.password");
    }

    private ClientSessionFactory factory() {
        ClientSessionFactory f = factory;
        if (f == null) {
            synchronized (this) {
                f = factory;
                if (f == null) {
                    factory = f = createFactory();
                }
            }
        }
        return f;
    }

    private ClientSessionFactory createFactory() {
        try {
            ServerLocator l = ActiveMQClient.createServerLocator(url());
            l.setReconnectAttempts(-1);          // reconnect forever; survive broker restarts
            l.setBlockOnDurableSend(false);      // async sends; delivery is confirmed through a callback
            l.setBlockOnNonDurableSend(false);
            l.setConfirmationWindowSize(ACK_BATCH_SIZE); // enables SendAcknowledgementHandler callbacks
            locator = l;
            return l.createSessionFactory();
        } catch (Exception e) {
            throw new MessagingException("Cannot connect to Artemis at " + url(), e);
        }
    }

    /** A producer session: sends are auto-committed; acks are irrelevant. */
    private ClientSession createProducerSession() {
        try {
            return factory().createSession(user(), password(), false, true, true, false, ACK_BATCH_SIZE);
        } catch (ActiveMQException e) {
            throw new MessagingException("Cannot create Artemis producer session", e);
        }
    }

    /** A consumer session: acks are committed explicitly so we honour {@code maxCommitInterval}. */
    ClientSession createConsumerSession() {
        try {
            return factory().createSession(user(), password(), false, true, false, false, ACK_BATCH_SIZE);
        } catch (ActiveMQException e) {
            throw new MessagingException("Cannot create Artemis consumer session", e);
        }
    }

    @Override
    public MessageProducer getProducer(String name) {
        return producers.computeIfAbsent(name, this::createProducer);
    }

    private ArtemisMessageProducer createProducer(String name) {
        String clientId = makeClientId(name);
        return new ArtemisMessageProducer(this, clientId, createProducerSession(), maxMessageSize, warnMessageSize);
    }

    /**
     * Routing type for {@code topic}, derived from its declared scope: a per-server broadcast
     * ({@link Topics#isBroadcast}) fans a copy out to every server (pub/sub) &rarr; MULTICAST; a cluster-shared work
     * queue balances one copy across the cluster (point-to-point) &rarr; ANYCAST. This is a static property of the
     * topic, so producers and consumers agree on it without either having to observe the other's registration.
     */
    RoutingType routingFor(String topic) {
        return Topics.isBroadcast(topic) ? RoutingType.MULTICAST : RoutingType.ANYCAST;
    }

    @Override
    public synchronized void closeProducers() {
        try {
            producers.forEach((_, p) -> p.close());
            producers.clear();
        } catch (Exception e) {
            logger.warn("Exception in closing producers", e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            ArtemisConsumer consumer;
            while ((consumer = consumers.poll()) != null) consumer.close();
        } catch (Exception e) {
            logger.warn("Exception in closing consumers", e);
        }
        closeProducers();
        closeTransport();
    }

    private void closeTransport() {
        try {
            if (factory != null) {
                factory.close();
                factory = null;
            }
            if (locator != null) {
                locator.close();
                locator = null;
            }
        } catch (Exception e) {
            logger.warn("Exception in closing Artemis transport", e);
        }
    }

    private synchronized void addConsumer(ArtemisConsumer consumer) {
        consumers.add(consumer);
        consumer.start();
    }

    private String makeClientId(String name) {
        return RuntimeIdentity.INSTANCE_ID + '-' + name + '-' + clientIndex.getAndIncrement();
    }

    /**
     * Resolve a listener's routing from the topic declaration (the single source of truth shared with producers) and
     * flag any disagreement with the caller's {@link ConsumerGroupScope}: a broadcast topic must be consumed per consumer
     * (PER_SERVER or PER_LISTENER) and a shared topic SHARED, otherwise the queue naming (which still follows
     * {@code groupScope}) and the routing diverge.
     */
    private RoutingType resolveRouting(String topic, ConsumerGroupScope consumerGroupScope, String clientId) {
        RoutingType rt = routingFor(topic);
        boolean broadcast = rt == RoutingType.MULTICAST;
        if (broadcast == (consumerGroupScope == ConsumerGroupScope.SHARED)) {
            logger.error("{} GroupScope={} disagrees with Topics.isBroadcast({})={}; routing follows the topic declaration ({})",
                clientId, consumerGroupScope, topic, broadcast, rt);
        }
        return rt;
    }

    @Override
    public void setMessageListener(String topic, MessageListener listener, int maxPollInterval, int maxPollRecords) {
        setMessageListener(topic, listener, maxPollInterval, maxPollRecords, 0, ConsumerGroupScope.SHARED);
    }

    @Override
    public void setMessageListener(String topic, MessageListener listener, int maxPollInterval, int maxPollRecords,
        long maxCommitInterval, ConsumerGroupScope consumerGroupScope)
    {
        logger.debug("setMessageListener() topic={}, listener={}", topic, listener.getClass().getName());

        String clientId = makeClientId(MessagingUtils.makeClientName(listener));
        RoutingType rt = resolveRouting(topic, consumerGroupScope, clientId);

        String queue = MessagingUtils.makeGroupName(topic, consumerGroupScope);
        int maxPollIntervalMs = MessagingUtils.makeMaxPollIntervalMs(maxPollInterval, clientId);
        maxCommitInterval = MessagingUtils.makeMaxCommitInterval(maxCommitInterval, maxPollIntervalMs);

        addConsumer(new ArtemisConsumer(this, topic, queue, rt, listener, clientId, maxCommitInterval));

        logger.warn("{} listener started on topic={}, queue={}, routing={}, maxCommitInterval={}",
            clientId, topic, queue, rt, maxCommitInterval);
    }

    @Override
    public void setMessageListeners(String topic, MessageListener listener, ConsumerConfig config) {
        setMessageListeners(topic, listener, config, ConsumerGroupScope.SHARED);
    }

    @Override
    public void setMessageListeners(String topic, MessageListener listener, ConsumerConfig config, ConsumerGroupScope consumerGroupScope) {
        int poolSize = config.poolSize();
        if (poolSize < 1) {
            logger.error("Wrong pool size={} for topic={}", poolSize, topic);
            poolSize = 1;
        }

        String clientId = makeClientId(MessagingUtils.makeClientName(listener));
        RoutingType rt = resolveRouting(topic, consumerGroupScope, clientId);
        String queue = MessagingUtils.makeGroupName(topic, consumerGroupScope);
        int maxPollIntervalMs = MessagingUtils.makeMaxPollIntervalMs(config.maxPollInterval(), clientId);
        long maxCommitInterval = MessagingUtils.makeMaxCommitInterval(0, maxPollIntervalMs);

        if (config.mode() == ConsumerMode.CONCURRENT) {
            // One consumer feeding a poolSize-thread pool: records fan out and are processed concurrently, so any
            // producer-stamped group id is ignored and order is not preserved (parity with Kafka's thread pool).
            addConsumer(new ArtemisPoolConsumer(this, topic, queue, rt, listener, clientId, maxCommitInterval, poolSize));
        } else {
            // ORDERED: poolSize competing consumers on the same queue; Artemis message grouping pins each key to one
            // of them, preserving per-key order across the pool.
            for (int i = 0; i < poolSize; i++) {
                String subClientId = poolSize > 1 ? clientId + '-' + i : clientId;
                addConsumer(new ArtemisConsumer(this, topic, queue, rt, listener, subClientId, maxCommitInterval));
            }
        }

        logger.warn("{} {} listeners started on topic={}, queue={}, routing={}, poolSize={}, maxCommitInterval={}",
            clientId, config.mode(), topic, queue, rt, poolSize, maxCommitInterval);
    }
}
