package dev.olegz.vf.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import dev.olegz.vf.common.RuntimeIdentity;
import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.messaging.*;
import dev.olegz.vf.messaging.consumer.ConsumerGroupScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CaffeineCacheInvalidator implements MessageListener {
    private static final Logger logger = LoggerFactory.getLogger(CaffeineCacheInvalidator.class);

    static final String INSTANCE_ID = RuntimeIdentity.INSTANCE_ID;

    static void registerInvalidator(CaffeineCacheInvalidationManager cacheManager) {
        // Every invalidator must see every invalidation, including several in the same JVM, so each consumes under
        // its own group (PER_LISTENER).
        try {
            Messaging.broker(MessagingProvider.KAFKA, ScopedMessageBroker.class).setMessageListener(Topics.CACHE_INVALIDATION,
                new CaffeineCacheInvalidator(cacheManager), 15, 20, 0, ConsumerGroupScope.PER_LISTENER);
        } catch (Exception e) {
            logger.error("Exception in setting CaffeineCacheInvalidator: " + e);
        }
    }

    private final long ttlMillis = expiryMillis();
    private final CaffeineCacheInvalidationManager cacheManager;

    private CaffeineCacheInvalidator(CaffeineCacheInvalidationManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Returns the invalidation-message TTL in milliseconds. Configured by
     * {@code vf.cache.mq.expiry} in seconds; the default is 600 seconds.
     */
    private static long expiryMillis() {
        return 1000L * PropertyStore.getLong("vf.cache.mq.expiry", 600L);
    }

    @Override
    public long messageTtlMillis() {
        return ttlMillis;
    }

    @Override
    public void onMessage(byte[] messageBody) {
        if (cacheManager == null) {
            logger.error("Cache manager not set");
            return;
        }

        CacheInvalidationEvent event = null;

        try {
            event = BytesMapper.readValue(messageBody, CacheInvalidationEvent.class);

            if (INSTANCE_ID.equals(event.server) || (event.cache == null)) return;

            Cache<Object, Object> cache = cacheManager.getCaffeineCache(event.cache);
            if (cache == null) return;

            if (event.key == null) {
                cache.invalidateAll();
            } else {
                cache.invalidate(event.key);
            }
        } catch (Exception e) {
            logger.error("Exception in updating cache for " + event, e);
        }
    }
}
