package dev.olegz.vf.registry.config;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.registry.dao.handlers.DatetimeTypeHandler;
import dev.olegz.vf.registry.dao.retry.RetryOnConcurrencyInterceptor;
import dev.olegz.vf.registry.dao.retry.RetryOnRecoverableInterceptor;
import dev.olegz.vf.registry.dao.translator.CustomSqlSessionTemplate;
import org.apache.ibatis.logging.slf4j.Slf4jImpl;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSessionFactory;
import org.jspecify.annotations.NonNull;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Data access Spring configuration (replaces the legacy XML configuration).
 * <p>
 * Wires the data source, transaction manager, ordered DAO interceptors, and the
 * MyBatis session factory/template plus mapper scanning.
 */
@Configuration
@EnableTransactionManagement(order = 100)
@MapperScan(basePackages = "dev.olegz.vf.registry.dao.mapper", sqlSessionTemplateRef = "sqlSessionTemplate")
public class DataSourceConfig {

    // --- DataSources -----------------------------------------------------------------

    @Bean(destroyMethod = "close")
    public PooledDataSource dataSource() {
        return new PooledDataSource();
    }

    // --- Transactions ----------------------------------------------------------------

    @Bean
    public DataSourceTransactionManager txManager() {
        return new DataSourceTransactionManager(dataSource());
    }

    // --- DAO interceptors / aspects (order defines the proxy nesting) -----------------

    /** Retries the DAO method on RecoverableDataAccessException. */
    @Bean
    public RetryOnRecoverableInterceptor retryOnRecoverableInterceptor() {
        RetryOnRecoverableInterceptor interceptor = new RetryOnRecoverableInterceptor();
        interceptor.setOrder(30);
        return interceptor;
    }

    /** Retries the annotated DAO method on ConcurrencyFailureException. */
    @Bean
    public RetryOnConcurrencyInterceptor retryOnConcurrencyInterceptor() {
        RetryOnConcurrencyInterceptor interceptor = new RetryOnConcurrencyInterceptor();
        interceptor.setOrder(40);
        return interceptor;
    }

    // --- MyBatis ---------------------------------------------------------------------

    private static final @NonNull String MAPPER_LOCATION_TEMPLATE = "classpath*:vf/sqlmaps/*.xml";

    /** MyBatis session factory. See http://www.mybatis.org/spring/factorybean.html */
    @Bean
    public SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource());
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(MAPPER_LOCATION_TEMPLATE));
        factoryBean.setConfiguration(createConfiguration());
        return factoryBean.getObject();
    }

    // See https://mybatis.org/mybatis-3/configuration.html
    private static org.apache.ibatis.session.@NonNull Configuration createConfiguration() {
        var configuration = new org.apache.ibatis.session.Configuration();
        configuration.setCacheEnabled(false);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.setMapUnderscoreToCamelCase(true);
        // when an unknown column is detected: NONE, WARNING, FAILING
        configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        // reuses prepared statements
        configuration.setDefaultExecutorType(ExecutorType.REUSE);
        configuration.setLogImpl(Slf4jImpl.class);
        configuration.getTypeHandlerRegistry().register(Datetime.class, DatetimeTypeHandler.class);
        return configuration;
    }

    /** MyBatis session template with custom SQL error codes translator. */
    @Bean
    public CustomSqlSessionTemplate sqlSessionTemplate() throws Exception {
        return new CustomSqlSessionTemplate(sqlSessionFactory());
    }
}
