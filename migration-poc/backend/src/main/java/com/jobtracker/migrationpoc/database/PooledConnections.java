package com.jobtracker.migrationpoc.database;

import com.jobtracker.migrationpoc.observability.RequestTiming;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/** Small pool registry for the legacy and writable PostgreSQL URLs used during parallel operation. */
public final class PooledConnections {
    private static final ConcurrentHashMap<String, HikariDataSource> POOLS = new ConcurrentHashMap<>();

    private PooledConnections() {}

    public static Connection open(LegacyDatabaseUrl database, Properties properties) throws SQLException {
        String username = properties.getProperty("user", "");
        String key = database.jdbcUrl() + "\n" + username;
        HikariDataSource source;
        try {
            source = POOLS.computeIfAbsent(key, ignored -> create(database, properties));
        } catch (PoolCreationException exception) {
            throw new SQLException("Unable to initialize PostgreSQL connection pool", exception.getCause());
        }
        long startedAt = System.nanoTime();
        try { return timed(source.getConnection()); }
        finally { RequestTiming.record("db", System.nanoTime() - startedAt); }
    }

    private static HikariDataSource create(LegacyDatabaseUrl database, Properties properties) {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(database.jdbcUrl());
            if (properties.getProperty("user") != null) config.setUsername(properties.getProperty("user"));
            if (properties.getProperty("password") != null) config.setPassword(properties.getProperty("password"));
            config.setPoolName("job-tracker-" + Integer.toHexString(database.jdbcUrl().hashCode()));
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(0);
            config.setConnectionTimeout(10_000);
            config.setValidationTimeout(3_000);
            config.setIdleTimeout(300_000);
            config.setMaxLifetime(1_500_000);
            String applicationName = properties.getProperty("ApplicationName", "job-tracker-migration-poc");
            config.addDataSourceProperty("ApplicationName", applicationName);
            return new HikariDataSource(config);
        } catch (RuntimeException exception) {
            throw new PoolCreationException(exception);
        }
    }

    private static Connection timed(Connection connection) { return proxy(Connection.class, connection); }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, T delegate) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, arguments) -> {
            boolean sqlExecution = delegate instanceof Statement && method.getName().startsWith("execute");
            long startedAt = sqlExecution ? System.nanoTime() : 0;
            try {
                Object value = method.invoke(delegate, arguments);
                if (value instanceof CallableStatement statement) return proxy(CallableStatement.class, statement);
                if (value instanceof PreparedStatement statement) return proxy(PreparedStatement.class, statement);
                if (value instanceof Statement statement) return proxy(Statement.class, statement);
                return value;
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            } finally {
                if (sqlExecution) RequestTiming.record("sql", System.nanoTime() - startedAt);
            }
        });
    }
    private static final class PoolCreationException extends RuntimeException {
        private PoolCreationException(Throwable cause) { super(cause); }
    }
}