package dev.olegz.vf.registry.config;

import dev.olegz.vf.common.props.PropertyStore;

public class PooledDataSource extends org.apache.tomcat.jdbc.pool.DataSource {
    public static final String DRIVER_CLASS_NAME = org.postgresql.Driver.class.getName();
    private static final String VALIDATION_QUERY = "/* ping */ SELECT 1";

    public PooledDataSource() {
        // https://tomcat.apache.org/tomcat-10.0-doc/jdbc-pool.html#Common_Attributes
        this.setDefaultAutoCommit(true);
        String url = PropertyStore.getString("jdbc.url");
        this.setDriverClassName(DRIVER_CLASS_NAME);
        this.setUrl(url + PropertyStore.getString("jdbc.timeout", ""));
        this.setUsername(PropertyStore.getString("jdbc.user"));
        this.setPassword(PropertyStore.decrypt("jdbc.password"));

        int maxActive = PropertyStore.getInt("jdbc.maxActiveConnections", 100);
        // The maximum number of active connections that can be allocated from this pool at the same time.
        this.setMaxActive(maxActive);
        // The maximum number of connections that should be kept in the pool at all times.
        // Idle connections are checked periodically and connections that been idle for longer than minEvictableIdleTimeMillis will be released.
        this.setMaxIdle(maxActive);
        // The minimum number of established connections that should be kept in the pool at all times.
        this.setMinIdle(PropertyStore.getInt("jdbc.minIdle", 10));
        // The maximum number of milliseconds that the pool will wait (when there are no available connections)
        // for a connection to be returned before throwing an exception.
        this.setMaxWait(PropertyStore.getInt("jdbc.maxWait", 30_000));
        // Time in milliseconds to keep this connection.
        // This attribute works both when returning connection and when borrowing connection.
        // When a connection is borrowed from the pool, the pool will check to see,
        // if the (now - time-when-connected) > maxAge has been reached, and if so, it reconnects before borrow it.
        // When a connection is returned to the pool, the pool will check to see,
        // if the (now - time-when-connected) > maxAge has been reached, and if so, it closes the connection rather than returning it to the pool.
        // The default value 0 implies that connections will be left open and no age check will be done upon borrowing from the pool, returning the connection to the pool or when checking idle connections.
        this.setMaxAge(PropertyStore.getLong("jdbc.maxAge", 0L));
        // The indication of whether objects will be validated before being borrowed from the pool.
        this.setTestOnBorrow(true);
        // The indication of whether objects will be validated before being returned to the pool.
        this.setTestOnReturn(true);
        // The indication of whether objects will be validated by the idle object evictor (if any).
        // If an object fails to validate, it will be dropped from the pool.
        this.setTestWhileIdle(true);
        // Avoid excess validation, only run validation at most at this frequency - time in milliseconds.
        // If a connection is due for validation, but has been validated previously within this interval, it will not be validated again.
        this.setValidationInterval(PropertyStore.getLong("jdbc.validationInterval", 3000L));
        // The SQL query that will be used to validate connections from this pool before returning them to the caller.
        this.setValidationQuery(VALIDATION_QUERY);
        // The minimum amount of time an object may sit idle in the pool before it is eligible for eviction.
        this.setMinEvictableIdleTimeMillis(PropertyStore.getInt("jdbc.minEvictableIdleTime", 30_000));
        // The number of milliseconds to sleep between runs of the idle connection validation/cleaner thread.
        // This value should not be set under 1 second.
        // It dictates how often we check for idle, abandoned connections, and how often we validate idle connections.
        this.setTimeBetweenEvictionRunsMillis(PropertyStore.getInt("jdbc.timeBetweenEvictionRuns", 30_000));
        // Flag to remove abandoned connections if they exceed the removeAbandonedTimeout.
        // If set to true a connection is considered abandoned and eligible for removal, if it has been in use longer than the removeAbandonedTimeout
        this.setRemoveAbandoned(true);
        // Timeout in seconds before an abandoned (in use) connection can be removed.
        // The value should be set to the longest running query your applications might have.
        this.setRemoveAbandonedTimeout(PropertyStore.getInt("jdbc.removeAbandonedTimeout", 300));
    }
}
