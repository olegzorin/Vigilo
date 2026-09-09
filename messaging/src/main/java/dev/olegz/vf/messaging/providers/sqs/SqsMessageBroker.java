package dev.olegz.vf.messaging.providers.sqs;

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
import dev.olegz.vf.messaging.support.MessagingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AWS SQS implementation of {@link MessageBroker}.
 * <p>
 * The provider identity is {@link MessagingProvider#SQS}. The broker is discovered via
 * {@link java.util.ServiceLoader} and should be obtained through {@link Messaging#broker(MessagingProvider)}, not by
 * constructing this class directly. All AWS operations stay behind {@code SqsSupport}; this class does not build SDK
 * clients itself.
 * <p>
 * SQS is a work-queue broker. Shared topics map naturally to one queue per topic. Broadcast scopes
 * ({@link ConsumerGroupScope#PER_SERVER}/{@link ConsumerGroupScope#PER_LISTENER}) need SNS or an explicit queue registry above SQS,
 * so this broker exposes only the base shared-listener contract. Consumers accept both Base64 payloads produced by
 * {@link SqsMessageProducer} and raw SQS bodies from external producers such as AWS Lambda destinations.
 */
public class SqsMessageBroker implements MessageBroker {
    private static final Logger logger = LoggerFactory.getLogger(SqsMessageBroker.class);

    private final ConcurrentHashMap<String, SqsMessageProducer> producers = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<SqsConsumer> consumers = new ConcurrentLinkedQueue<>();
    private final AtomicInteger clientIndex = new AtomicInteger(0);

    private final int maxMessageSize = PropertyStore.getInt("vf.sqs.maxSize", MessageBroker.DEFAULT_MAX_MESSAGE_SIZE);
    private final int warnMessageSize = PropertyStore.getInt("vf.sqs.warnSize", maxMessageSize * 3 / 4);
    private final int waitTimeSeconds = PropertyStore.getInt("vf.sqs.waitTimeSeconds", 20);
    private final int visibilityTimeoutSeconds = PropertyStore.getInt("vf.sqs.visibilityTimeoutSeconds", 60);
    private final int messageRetentionSeconds = PropertyStore.getInt("vf.sqs.messageRetentionSeconds", 345600);

    /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
    public SqsMessageBroker() {
        ShutdownManager.register(this::close);
    }

    @Override
    public MessagingProvider provider() {
        return MessagingProvider.SQS;
    }

    @Override
    public MessageProducer getProducer(String name) {
        return producers.computeIfAbsent(name, this::createProducer);
    }

    private SqsMessageProducer createProducer(String name) {
        return new SqsMessageProducer(makeClientId(name), maxMessageSize, warnMessageSize,
            messageRetentionSeconds, visibilityTimeoutSeconds);
    }

    @Override
    public void setMessageListener(String topic, MessageListener listener, int maxPollInterval, int maxPollRecords)
    {
        logger.debug("setMessageListener() topic={}, listener={}", topic, listener.getClass().getName());

        String clientId = makeClientId(MessagingUtils.makeClientName(listener));
        maxPollRecords = checkMaxPollRecords(maxPollRecords, clientId);

        SqsConsumer consumer = new SqsConsumer(topic, topic, listener, clientId, maxPollRecords, waitTimeSeconds,
            visibilityTimeoutSeconds, 1);
        addConsumer(consumer);

        logger.warn("{} SQS listener started on topic={}, maxPollRecords={}, waitTimeSeconds={}, visibilityTimeout={}",
            clientId, topic, maxPollRecords, waitTimeSeconds, visibilityTimeoutSeconds);
    }

    @Override
    public void setMessageListeners(String topic, MessageListener listener, ConsumerConfig config) {
        int poolSize = config.poolSize();
        if (poolSize < 1) {
            logger.error("Wrong pool size={} for topic={}", poolSize, topic);
            poolSize = 1;
        }

        String clientId = makeClientId(MessagingUtils.makeClientName(listener));
        int maxPollRecords = checkMaxPollRecords(config.maxPollRecords(), clientId);

        if (config.mode() == ConsumerMode.ORDERED && poolSize > 1) {
            for (int i = 0; i < poolSize; i++) {
                addConsumer(new SqsConsumer(topic, topic, listener, clientId + '-' + i, maxPollRecords,
                    waitTimeSeconds, visibilityTimeoutSeconds, 1));
            }
        } else {
            addConsumer(new SqsConsumer(topic, topic, listener, clientId, maxPollRecords, waitTimeSeconds,
                visibilityTimeoutSeconds, poolSize));
        }

        logger.warn("{} SQS listeners started on topic={}, mode={}, poolSize={}, maxPollRecords={}, waitTimeSeconds={}, visibilityTimeout={}",
            clientId, topic, config.mode(), poolSize, maxPollRecords, waitTimeSeconds, visibilityTimeoutSeconds);
    }

    @Override
    public int maxMessageSize() {
        return maxMessageSize;
    }

    @Override
    public synchronized void closeProducers() {
        try {
            producers.values().forEach(SqsMessageProducer::close);
            producers.clear();
        } catch (Exception e) {
            logger.warn("Exception in closing producers", e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            SqsConsumer consumer;
            while ((consumer = consumers.poll()) != null) consumer.close();
        } catch (Exception e) {
            logger.warn("Exception in closing SQS consumers", e);
        }
        closeProducers();
    }

    private void addConsumer(SqsConsumer consumer) {
        consumers.add(consumer);
        consumer.start();
    }

    private String makeClientId(String name) {
        return RuntimeIdentity.INSTANCE_ID + '-' + name + '-' + clientIndex.getAndIncrement();
    }

    private int checkMaxPollRecords(int maxPollRecords, String clientId) {
        maxPollRecords = MessagingUtils.checkMaxPollRecords(maxPollRecords, clientId);
        if (maxPollRecords > 10) {
            logger.warn("{} SQS maxNumberOfMessages is 10; reducing maxPollRecords={}", clientId, maxPollRecords);
            return 10;
        }
        return maxPollRecords;
    }
}
