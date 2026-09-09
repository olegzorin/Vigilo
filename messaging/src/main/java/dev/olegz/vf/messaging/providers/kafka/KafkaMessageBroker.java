package dev.olegz.vf.messaging.providers.kafka;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import dev.olegz.vf.common.RuntimeIdentity;
import dev.olegz.vf.common.ShutdownManager;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.messaging.*;
import dev.olegz.vf.messaging.consumer.ConsumerGroupScope;
import dev.olegz.vf.messaging.consumer.ConsumerMode;
import dev.olegz.vf.messaging.support.MessagingUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka implementation of {@link MessageBroker}.
 * <p>
 * The provider identity is {@link MessagingProvider#KAFKA}. The broker is discovered via
 * {@link java.util.ServiceLoader} (see
 * {@code META-INF/services/dev.olegz.vf.messaging.MessageBroker}) and should be obtained through
 * {@link Messaging#broker(MessagingProvider)}, not by constructing this class directly.
 * <p>
 * Kafka is the general application streaming broker: topics map to Kafka topics, keyed sends preserve per-key order
 * by routing related records to the same partition, and commits are managed by the consumer implementations.
 */
public class KafkaMessageBroker implements ScopedMessageBroker {
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageBroker.class);

    private static final String SERIALIZER_CLASS = ByteArraySerializer.class.getName();
    private static final String DESERIALIZER_CLASS = ByteArrayDeserializer.class.getName();

    private final ConcurrentHashMap<String, KafkaMessageProducer> producers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<TopicConsumer>> listeners = new ConcurrentHashMap<>();
    private final AtomicInteger clientIndex = new AtomicInteger(0);
    private final int maxMessageSize = PropertyStore.getInt("vf.kafka.maxSize", MessageBroker.DEFAULT_MAX_MESSAGE_SIZE);
    // Soft threshold for the "large message" warning; defaults to 75% of the hard cap so it fires before max.request.size rejects the record.
    // Note: this is checked against the UNCOMPRESSED payload size, whereas max.request.size below is enforced by Kafka on the lz4-compressed
    // record. A payload can therefore trip this warning yet still send comfortably under the wire limit once compressed.
    private final int warnMessageSize = PropertyStore.getInt("vf.kafka.warnSize", maxMessageSize * 3 / 4);

    /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
    public KafkaMessageBroker() {
        // Close consumers on application shutdown without 'common' depending on this module.
        ShutdownManager.register(this::close);
    }

    private static String servers() {
        String servers = StringUtils.trimToNull(PropertyStore.getString("vf.kafka.servers"));
        if (servers == null) throw new MessagingException("Kafka servers not configured");
        return servers;
    }

    @Override
    public MessagingProvider provider() {
        return MessagingProvider.KAFKA;
    }

    @Override
    public int maxMessageSize() {
        return maxMessageSize;
    }

    @Override
    public MessageProducer getProducer(String name) {
        return producers.computeIfAbsent(name, this::createProducer);
    }

    private KafkaMessageProducer createProducer(String name) {
        String clientId = makeClientId(name);

        // Producer config: http://kafka.apache.org/documentation/#producerconfigs
        HashMap<String, Object> props = new HashMap<>(16);
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, servers()); // comma separated list of host:port pairs
        props.put(ProducerConfig.ACKS_CONFIG, "1"); // 0 = no ack, 1 = only leader ack, -1/all = all replicas ack
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432); // total bytes of memory to buffer records
        props.put(ProducerConfig.RETRIES_CONFIG, 10); // resend any record whose send fails
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 32768); // default batch size in bytes
        props.put(ProducerConfig.LINGER_MS_CONFIG, 200); // artificial delay in sending batches
        props.put(ProducerConfig.CLIENT_ID_CONFIG, clientId); // logical application name
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4"); // none, gzip, snappy, lz4, zstd
        // Hard cap on an uncompressed record: the producer rejects anything larger with RecordTooLargeException.
        // Kept equal to maxMessageSize (the value callers guard on) so the two never drift; must be <= the broker's message.max.bytes / topic max.message.bytes.
        props.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, maxMessageSize);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, SERIALIZER_CLASS);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, SERIALIZER_CLASS);

        return new KafkaMessageProducer(clientId, props, maxMessageSize, warnMessageSize);
    }

    @Override
    public synchronized void closeProducers() {
        try {
            producers.values().forEach(KafkaMessageProducer::close);
            producers.clear();
        } catch (Exception e) {
            logger.warn("Exception in closing producers", e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            TopicConsumer consumer;
            for (ConcurrentLinkedQueue<TopicConsumer> queue : listeners.values()) {
                while ((consumer = queue.poll()) != null) consumer.close();
            }
        } catch (Exception e) {
            logger.warn("Exception in closing listener pools", e);
        }
    }

    private synchronized void addListener(String topic, TopicConsumer consumer) {
        listeners.computeIfAbsent(topic, _ -> new ConcurrentLinkedQueue<>()).add(consumer);
        consumer.start();
    }

    private String makeClientId(String name) {
        return RuntimeIdentity.INSTANCE_ID + '-' + name + '-' + clientIndex.getAndIncrement();
    }

    static HashMap<String, Object> consumerProperties(String group, int maxPollIntervalMs, int maxPollRecords, String clientId) {
        // Consumer config: http://kafka.apache.org/documentation/#consumerconfigs
        HashMap<String, Object> props = new HashMap<>(16);
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, servers());
        // A unique string that identifies the consumer group this consumer belongs to.
        props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        // Expected time between heartbeats to the coordinator; should be no higher than 1/3 of session.timeout.ms.
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3000);
        // Timeout to detect consumer failures; on expiry the broker removes the consumer and rebalances.
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 15000);
        // Maximum delay between poll() calls before the consumer is considered failed and the group rebalances.
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, maxPollIntervalMs);
        // Maximum number of records returned in a single poll().
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, DESERIALIZER_CLASS);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, DESERIALIZER_CLASS);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId);

        return props;
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
        String group = MessagingUtils.makeGroupName(topic, consumerGroupScope);
        int maxPollIntervalMs = MessagingUtils.makeMaxPollIntervalMs(maxPollInterval, clientId);
        maxPollRecords = MessagingUtils.checkMaxPollRecords(maxPollRecords, clientId);
        maxCommitInterval = MessagingUtils.makeMaxCommitInterval(maxCommitInterval, maxPollIntervalMs);

        HashMap<String, Object> props = consumerProperties(group, maxPollIntervalMs, maxPollRecords, clientId);

        SyncConsumer consumer = new SyncConsumer(topic, props, listener, clientId, maxCommitInterval);
        addListener(topic, consumer);

        logger.warn("{} listener started on topic={}, maxPollInterval={}, maxPollRecords={}, maxCommitInterval={}",
            clientId, topic, maxPollIntervalMs, maxPollRecords, maxCommitInterval);
    }

    @Override
    public void setMessageListeners(String topic, MessageListener listener,
        dev.olegz.vf.messaging.consumer.ConsumerConfig config)
    {
        setMessageListeners(topic, listener, config, ConsumerGroupScope.SHARED);
    }

    @Override
    public void setMessageListeners(String topic, MessageListener listener,
        dev.olegz.vf.messaging.consumer.ConsumerConfig config, ConsumerGroupScope consumerGroupScope)
    {
        int poolSize = config.poolSize();
        if (poolSize < 0) {
            logger.error("Wrong pool size={} for topic={}", poolSize, topic);
            poolSize = 1;
        }

        if (config.mode() == ConsumerMode.CONCURRENT) {
            setThreadPoolConsumer(topic, listener, poolSize, config.maxPollInterval(), config.maxPollRecords(), consumerGroupScope);
        } else {
            setMultiConsumers(topic, listener, poolSize, config.maxPollInterval(), config.maxPollRecords(), consumerGroupScope);
        }
    }

    private void setThreadPoolConsumer(String topic, MessageListener listener, int threadPoolSize,
        int maxPollInterval, int maxPollRecords, ConsumerGroupScope consumerGroupScope)
    {
        logger.debug("setThreadPoolConsumer() topic={}, listener={}, threadPoolSize={}",
            topic, listener.getClass().getName(), threadPoolSize);

        String clientId = makeClientId(MessagingUtils.makeClientName(listener));
        String group = MessagingUtils.makeGroupName(topic, consumerGroupScope);
        int maxPollIntervalMs = MessagingUtils.makeMaxPollIntervalMs(maxPollInterval, clientId);
        long maxCommitInterval = MessagingUtils.makeMaxCommitInterval(0, maxPollIntervalMs);

        if (threadPoolSize > 1) {
            if (maxPollRecords > threadPoolSize) {
                logger.warn("Too big maxPollRecords={} for clientId={}, poolSize={}", maxPollRecords, clientId, threadPoolSize);
                maxPollRecords = threadPoolSize;
            } else {
                maxPollRecords = MessagingUtils.checkMaxPollRecords(maxPollRecords, clientId);
            }
        } else if (threadPoolSize == 1) {
            maxPollRecords = 1;
        } else {
            maxPollRecords = MessagingUtils.checkMaxPollRecords(maxPollRecords, clientId);
        }

        HashMap<String, Object> props = consumerProperties(group, maxPollIntervalMs, maxPollRecords, clientId);

        AsyncConsumer poolConsumer = new AsyncConsumer(topic, props, listener, clientId, threadPoolSize, maxCommitInterval);
        addListener(topic, poolConsumer);

        logger.warn("{} thread pool started on topic={}, poolSize={}, maxPollInterval={}, maxPollRecords={}, maxCommitInterval={}",
            clientId, topic, threadPoolSize, maxPollIntervalMs, maxPollRecords, maxCommitInterval);
    }

    private void setMultiConsumers(String topic, MessageListener listener,
        int consumersNum, int maxPollInterval, int maxPollRecords, ConsumerGroupScope consumerGroupScope)
    {
        logger.debug("setMessageListeners() topic={}, listener={}, consumersNum={}",
            topic, listener.getClass().getName(), consumersNum);

        String clientId = makeClientId(MessagingUtils.makeClientName(listener));
        String group = MessagingUtils.makeGroupName(topic, consumerGroupScope);
        int maxPollIntervalMs = MessagingUtils.makeMaxPollIntervalMs(maxPollInterval, clientId);
        maxPollRecords = MessagingUtils.checkMaxPollRecords(maxPollRecords, clientId);
        long maxCommitInterval = MessagingUtils.makeMaxCommitInterval(0, maxPollIntervalMs);

        HashMap<String, Object> props = consumerProperties(group, maxPollIntervalMs, maxPollRecords, clientId);

        // Each consumer needs its own partitions, so register consumersNum independent single-thread consumers
        // on the topic. The broker tracks them in the per-topic queue and shuts them all down together.
        for (int i = 0; i < consumersNum; i++) {
            String subClientId = clientId + '-' + i;
            props.put(ConsumerConfig.CLIENT_ID_CONFIG, subClientId);
            addListener(topic, new SyncConsumer(topic, props, listener, subClientId, maxCommitInterval));
        }

        logger.warn("{} listeners started on topic={}, consumersNum={}, maxPollInterval={}, maxPollRecords={}, maxCommitInterval={}",
            clientId, topic, consumersNum, maxPollIntervalMs, maxPollRecords, maxCommitInterval);
    }
}
