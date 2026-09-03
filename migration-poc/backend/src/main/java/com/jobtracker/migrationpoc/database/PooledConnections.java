package com.jobtracker.migrationpoc.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
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
        return source.getConnection();
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

    private static final class PoolCreationException extends RuntimeException {
        private PoolCreationException(Throwable cause) { super(cause); }
    }
}