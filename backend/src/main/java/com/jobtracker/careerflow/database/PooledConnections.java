package com.jobtracker.careerflow.database;

import com.jobtracker.careerflow.observability.RequestTiming;

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

/** Small pool registry for PostgreSQL migration sources and the local SQLite database. */
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
            throw new SQLException("Unable to initialize database connection pool", exception.getCause());
        }
        long startedAt = System.nanoTime();
        try { return timed(source.getConnection(), database); }
        finally { RequestTiming.record("db", System.nanoTime() - startedAt); }
    }

    private static HikariDataSource create(LegacyDatabaseUrl database, Properties properties) {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(database.jdbcUrl());
            if (properties.getProperty("user") != null) config.setUsername(properties.getProperty("user"));
            if (properties.getProperty("password") != null) config.setPassword(properties.getProperty("password"));
            config.setPoolName("job-tracker-" + Integer.toHexString(database.jdbcUrl().hashCode()));
            config.setMaximumPoolSize(database.isSqlite() ? 1 : 5);
            config.setMinimumIdle(0);
            config.setConnectionTimeout(10_000);
            config.setValidationTimeout(3_000);
            config.setIdleTimeout(300_000);
            config.setMaxLifetime(1_500_000);
            if (database.isSqlite()) {
                config.setConnectionInitSql("PRAGMA foreign_keys=ON");
                config.addDataSourceProperty("busy_timeout", "15000");
                config.addDataSourceProperty("journal_mode", "WAL");
            } else {
                String applicationName = properties.getProperty("ApplicationName", "careerflow");
                config.addDataSourceProperty("ApplicationName", applicationName);
            }
            return new HikariDataSource(config);
        } catch (RuntimeException exception) {
            throw new PoolCreationException(exception);
        }
    }

    private static Connection timed(Connection connection, LegacyDatabaseUrl database) {
        return proxy(Connection.class, connection, database);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, T delegate, LegacyDatabaseUrl database) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, arguments) -> {
            if (database.isSqlite() && delegate instanceof Connection) {
                if (method.getName().equals("setReadOnly")) return null;
                if (method.getName().equals("setTransactionIsolation")) {
                    arguments = new Object[]{Connection.TRANSACTION_SERIALIZABLE};
                }
                if ((method.getName().equals("prepareStatement") || method.getName().equals("prepareCall"))
                    && arguments != null && arguments.length > 0 && arguments[0] instanceof String sql) {
                    arguments = arguments.clone();
                    arguments[0] = DatabaseDialect.rewrite(database, sql);
                }
            }
            if (database.isSqlite() && delegate instanceof Statement
                && arguments != null && arguments.length > 0 && arguments[0] instanceof String sql) {
                arguments = arguments.clone();
                arguments[0] = DatabaseDialect.rewrite(database, sql);
            }
            boolean sqlExecution = delegate instanceof Statement && method.getName().startsWith("execute");
            long startedAt = sqlExecution ? System.nanoTime() : 0;
            try {
                Object value = method.invoke(delegate, arguments);
                if (value instanceof CallableStatement statement) return proxy(CallableStatement.class, statement, database);
                if (value instanceof PreparedStatement statement) return proxy(PreparedStatement.class, statement, database);
                if (value instanceof Statement statement) return proxy(Statement.class, statement, database);
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
