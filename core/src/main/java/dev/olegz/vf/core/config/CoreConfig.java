package dev.olegz.vf.core.config;

import dev.olegz.vf.core.cache.CaffeineCacheInvalidationManager;
import dev.olegz.vf.core.cache.MethodCacheKeyGenerator;
import dev.olegz.vf.core.service.cache.CacheInvalidationOutboxService;
import dev.olegz.vf.registry.config.RegistryConfig;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

/**
 * Core Spring configuration.
 * <p>
 * Enables AspectJ auto-proxying - needed so the DAO aspects declared in
 * {@link DataSourceConfig} are woven, including in the non-Boot {@code @ContextConfiguration}
 * test contexts that do not run Spring Boot AOP auto-configuration - annotation-driven
 * caching backed by the invalidating Caffeine cache manager, and component scanning of the
 * core service / DAO-impl layers and the AWS service implementations.
 */
@Configuration
@EnableAspectJAutoProxy
@EnableCaching
@Import({RegistryConfig.class, CoreDataSourceConfig.class})
@ComponentScan(basePackages = {
    "dev.olegz.vf.core.dao.impl",
    "dev.olegz.vf.core.service",
    "dev.olegz.vf.aws.s3",
    "dev.olegz.vf.aws.cloudwatch"
})
public class CoreConfig {

    /** Closes the long-lived AWS clients held by the static support classes on context shutdown. */
    @Bean
    public AwsClientLifecycle awsClientLifecycle() {
        return new AwsClientLifecycle();
    }

    /** Sole {@link org.springframework.cache.CacheManager}; resolved by {@code @EnableCaching}. */
    @Bean
    public CaffeineCacheInvalidationManager caffeineCacheInvalidationManager(
        CacheInvalidationOutboxService outboxService)
    {
        return new CaffeineCacheInvalidationManager(outboxService);
    }

    /** Key generator referenced by id ({@link MethodCacheKeyGenerator#ID}) in {@code @Cacheable} methods. */
    @Bean
    public MethodCacheKeyGenerator methodCacheKeyGenerator() {
        return new MethodCacheKeyGenerator();
    }
}
