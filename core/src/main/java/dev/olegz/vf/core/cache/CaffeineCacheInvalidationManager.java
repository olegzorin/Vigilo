package dev.olegz.vf.core.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Caffeine;
import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.service.cache.CacheInvalidationOutboxService;
import dev.olegz.vf.registry.cache.CacheNames;
import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;

/**
 * Caffeine cache invalidation: https://github.com/ben-manes/caffeine/wiki
 * Spring cache: https://docs.spring.io/spring/docs/current/spring-framework-reference/integration.html#cache
 *
 * Cache keys: any method whose key is not simply its sole argument must declare
 * {@code keyGenerator = }{@link MethodCacheKeyGenerator#ID}. That generator derives the key from the
 * method name plus the type and value of every argument, so the same logical call produces the same
 * key on every node - which is required for invalidated caches, where a key created on one node must
 * match the key invalidated on another. When a method takes a single argument and declares no
 * generator, Spring uses that argument itself as the key.
 * <p>
 * Invalidating mutations synchronously insert an outbox row before delegating to
 * {@link TransactionAwareCacheDecorator}. With transaction advice ordered outside cache advice, the business
 * write and outbox insert commit atomically, while the local Caffeine mutation is applied only after commit.
 */
public class CaffeineCacheInvalidationManager extends CaffeineCacheManager {
    private static final Logger logger = LoggerFactory.getLogger(CaffeineCacheInvalidationManager.class);

    private final ConcurrentHashMap<String, com.github.benmanes.caffeine.cache.Cache<Object, Object>> cacheMap = new ConcurrentHashMap<>(32);
    private final ConcurrentHashMap<String, Long> cacheMaxSize = new ConcurrentHashMap<>(32);
    private final CacheInvalidationOutboxService outboxService;

    public CaffeineCacheInvalidationManager(CacheInvalidationOutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @PostConstruct
    public void init() {
        CaffeineCacheInvalidator.registerInvalidator(this);
    }

    private long maxSize(String name, long defaultSize) {
        return cacheMaxSize.computeIfAbsent(name, _ -> PropertyStore.getLong("vf.cache.maxSize." + name, defaultSize));
    }

    @Override
    @NonNull
    protected Cache createCaffeineCache(@NonNull String name) {
        boolean invalidationEnabled = false;
        final Caffeine<Object, Object> caffeine = Caffeine.newBuilder().recordStats();

        switch (name) {
            // read-only caches
            case CacheNames.ORGANIZATION_MOVES -> caffeine
                .expireAfterWrite(3, TimeUnit.HOURS)
                .maximumSize(maxSize(name, 20));
            case CacheNames.CONSTANT_DICTIONARY -> caffeine
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .maximumSize(maxSize(name, 200));

            // invalidate all caches
            case CacheNames.SUB_ORGANIZATIONS -> {
                caffeine
                    .expireAfterWrite(15, TimeUnit.MINUTES)
                    .maximumSize(maxSize(name, 20));
                invalidationEnabled = true;
            }

            // remove by ID caches
            case CacheNames.ORGANIZATIONS_BY_ID -> {
                caffeine
                    .expireAfterWrite(1, TimeUnit.HOURS)
                    .maximumSize(maxSize(name, 200));
                invalidationEnabled = true;
            }
            case CacheNames.LAMBDA_RUNTIME_ASSIGNMENTS -> {
                caffeine
                    .expireAfterWrite(15, TimeUnit.MINUTES)
                    .maximumSize(maxSize(name, 1000));
                invalidationEnabled = true;
            }
            case CacheNames.TRIGGER_LOCATION_METADATA -> {
                caffeine
                    .expireAfterWrite(1, TimeUnit.HOURS)
                    .maximumSize(maxSize(name, 1000));
                invalidationEnabled = true;
            }
            case CacheNames.TRIGGER_LOCATION_DEVICE_METADATA -> {
                caffeine
                    .expireAfterWrite(15, TimeUnit.MINUTES)
                    .maximumSize(maxSize(name, 1000));
                invalidationEnabled = true;
            }
            case CacheNames.ORGANIZATIONS_BY_NAME -> {
                caffeine
                    .expireAfterWrite(1, TimeUnit.HOURS)
                    .maximumSize(maxSize(name, 20));
                invalidationEnabled = true;
            }

            // remove by ID and invalidate all caches
            case CacheNames.USERS_BY_ID -> {
                caffeine
                    .expireAfterAccess(15, TimeUnit.MINUTES)
                    .maximumSize(maxSize(name, 1000));
                invalidationEnabled = true;
            }
            default -> {
                logger.error("Unknown cache " + name);
                caffeine
                    .expireAfterWrite(15, TimeUnit.MINUTES)
                    .maximumSize(maxSize(name, 100));
            }
        }

        com.github.benmanes.caffeine.cache.Cache<Object, Object> cache = caffeine.build();
        cacheMap.put(name, cache);

        Cache localCache = new CaffeineCache(name, cache, true);
        return invalidationEnabled ?
            new InvalidatingCache(localCache, cache, outboxService) :
            new TransactionAwareCacheDecorator(localCache);
    }

    com.github.benmanes.caffeine.cache.Cache<Object, Object> getCaffeineCache(String name) {
        return cacheMap.get(name);
    }

    private static class InvalidatingCache extends TransactionAwareCacheDecorator {
        private final com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache;
        private final CacheInvalidationOutboxService outboxService;

        private InvalidatingCache(
            Cache targetCache,
            com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache,
            CacheInvalidationOutboxService outboxService)
        {
            super(targetCache);
            this.nativeCache = nativeCache;
            this.outboxService = outboxService;
        }

        @Override
        public void put(@NonNull Object key, @Nullable Object value) {
            // Invalidate only when this overwrites an existing entry. A brand-new entry cannot have made
            // other nodes stale - they will load it on their next miss - so no invalidation is needed.
            boolean update = nativeCache.getIfPresent(key) != null;
            if (update) enqueueEvent(key);
            super.put(key, value);
        }

        @Override
        public ValueWrapper putIfAbsent(@NonNull Object key, @Nullable Object value) {
            ValueWrapper res = super.putIfAbsent(key, value);
            if (res != null) enqueueEvent(key);
            return res;
        }

        @Override
        public void evict(@NonNull Object key) {
            enqueueEvent(key);
            super.evict(key);
        }

        @Override
        public boolean evictIfPresent(@NonNull Object key) {
            enqueueEvent(key);
            return super.evictIfPresent(key);
        }

        @Override
        public void clear() {
            enqueueEvent(null);
            super.clear();
        }

        @Override
        public boolean invalidate() {
            enqueueEvent(null);
            return super.invalidate();
        }

        private void enqueueEvent(Object key) {
            byte[] payload = BytesMapper.writeValue(new CacheInvalidationEvent(getName(), key));
            outboxService.enqueue(payload);
        }
    }
}
