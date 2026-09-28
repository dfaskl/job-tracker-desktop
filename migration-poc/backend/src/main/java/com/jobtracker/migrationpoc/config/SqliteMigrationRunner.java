package com.jobtracker.migrationpoc.config;

import com.jobtracker.migrationpoc.database.LegacyDatabaseUrl;
import com.jobtracker.migrationpoc.database.PooledConnections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Properties;

/** One-time, transactionally safe copy from the existing PostgreSQL database into an empty SQLite file. */
@Component
@Order(20)
public class SqliteMigrationRunner implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(SqliteMigrationRunner.class);
    private static final String MIGRATION_NAME = "postgresql-to-sqlite-v1";
    private static final List<Table> TABLES = List.of(
        new Table("interview_groups", "id,name,created_at"),
        new Table("users", "id,email,password_salt,password_hash,last_active_at,created_at,is_admin,disabled_at,display_name,group_id"),
        new Table("sessions", "token_hash,user_id,expires_at,last_active_at,created_at"),
        new Table("user_data", "user_id,data,updated_at"),
        new Table("company_links", "user_id,items,updated_at"),
        new Table("api_configs", "user_id,api_url,model,encrypted_api_key,encryption_iv,auth_tag,key_last_four,updated_at"),
        new Table("data_backups", "id,user_id,data,reason,created_at"),
        new Table("admin_audit_logs", "id,admin_user_id,target_user_id,target_email,action,created_at"),
        new Table("system_settings", "key,value,updated_by,updated_at"),
        new Table("mail_accounts", "id,user_id,email,provider,encrypted_password,encryption_iv,auth_tag,last_uid,last_synced_at,last_error,initialized,collect_after,created_at"),
        new Table("collected_mails", "id,user_id,account_id,message_uid,sender,subject,body,received_at,processed_at,created_at")
    );

    private final Environment environment;

    public SqliteMigrationRunner(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!Boolean.parseBoolean(environment.getProperty("SQLITE_MIGRATION_ENABLED", "false"))) return;
        String sourceRaw = environment.getProperty("MIGRATION_SOURCE_DATABASE_URL", "").trim();
        if (sourceRaw.isBlank()) throw new IllegalStateException("MIGRATION_SOURCE_DATABASE_URL is required");

        LegacyDatabaseUrl target = LegacyDatabaseUrl.parse(AppEnvironment.databaseUrl(environment));
        LegacyDatabaseUrl source = LegacyDatabaseUrl.parse(sourceRaw);
        if (!target.isSqlite()) throw new IllegalStateException("SQLite migration target must use jdbc:sqlite or sqlite URL");
        if (source.isSqlite()) throw new IllegalStateException("SQLite migration source must be PostgreSQL");

        Properties sourceProperties = properties(source, "job-tracker-sqlite-migration-source");
        Properties targetProperties = properties(target, "job-tracker-sqlite-migration-target");
        try (Connection sourceConnection = PooledConnections.open(source, sourceProperties);
             Connection targetConnection = PooledConnections.open(target, targetProperties)) {
            if (completed(targetConnection)) {
                LOGGER.info("SQLite migration {} was already completed; skipping", MIGRATION_NAME);
                return;
            }
            if (count(targetConnection, "users") != 0) {
                throw new IllegalStateException("SQLite target already contains users; refusing to merge databases");
            }
            sourceConnection.setReadOnly(true);
            sourceConnection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            sourceConnection.setAutoCommit(false);
            targetConnection.setAutoCommit(false);
            try {
                long totalRows = 0;
                for (Table table : TABLES) {
                    long copied = copy(sourceConnection, targetConnection, table);
                    totalRows += copied;
                    LOGGER.info("SQLite migration copied {} rows from {}", copied, table.name());
                }
                try (PreparedStatement marker = targetConnection.prepareStatement(
                    "INSERT INTO database_migrations(name,details) VALUES(?,?)"
                )) {
                    marker.setString(1, MIGRATION_NAME);
                    marker.setString(2, "copiedRows=" + totalRows);
                    marker.executeUpdate();
                }
                targetConnection.commit();
                sourceConnection.commit();
                LOGGER.info("SQLite migration completed successfully; copied {} rows", totalRows);
            } catch (Exception exception) {
                targetConnection.rollback();
                sourceConnection.rollback();
                throw exception;
            }
        }
    }

    private long copy(Connection source, Connection target, Table table) throws Exception {
        String[] columns = table.columns().split(",");
        String placeholders = String.join(",", java.util.Collections.nCopies(columns.length, "?"));
        String selectSql = "SELECT " + table.columns() + " FROM " + table.name();
        String insertSql = "INSERT INTO " + table.name() + "(" + table.columns() + ") VALUES(" + placeholders + ")";
        long copied = 0;
        try (PreparedStatement select = source.prepareStatement(selectSql);
             ResultSet rows = select.executeQuery();
             PreparedStatement insert = target.prepareStatement(insertSql)) {
            ResultSetMetaData metadata = rows.getMetaData();
            while (rows.next()) {
                for (int index = 1; index <= columns.length; index++) {
                    bind(insert, index, rows.getObject(index), metadata.getColumnTypeName(index));
                }
                insert.addBatch();
                copied++;
                if (copied % 500 == 0) insert.executeBatch();
            }
            insert.executeBatch();
        }
        long targetCount = count(target, table.name());
        long sourceCount = count(source, table.name());
        if (sourceCount != targetCount) {
            throw new IllegalStateException("Row count mismatch for " + table.name() + ": " + sourceCount + " != " + targetCount);
        }
        return copied;
    }

    private void bind(PreparedStatement statement, int index, Object value, String typeName) throws Exception {
        if (value == null) {
            statement.setObject(index, null);
        } else if (value instanceof Timestamp timestamp) {
            statement.setLong(index, timestamp.toInstant().toEpochMilli());
        } else if (value instanceof OffsetDateTime timestamp) {
            statement.setLong(index, timestamp.toInstant().toEpochMilli());
        } else if (value instanceof ZonedDateTime timestamp) {
            statement.setLong(index, timestamp.toInstant().toEpochMilli());
        } else if (value instanceof Instant timestamp) {
            statement.setLong(index, timestamp.toEpochMilli());
        } else if (value instanceof Boolean bool) {
            statement.setInt(index, bool ? 1 : 0);
        } else if (value instanceof byte[] bytes) {
            statement.setBytes(index, bytes);
        } else if ("json".equalsIgnoreCase(typeName) || "jsonb".equalsIgnoreCase(typeName)) {
            statement.setString(index, value.toString());
        } else if (value instanceof BigDecimal number) {
            statement.setBigDecimal(index, number);
        } else {
            statement.setObject(index, value);
        }
    }

    private boolean completed(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT 1 FROM database_migrations WHERE name=?"
        )) {
            statement.setString(1, MIGRATION_NAME);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private long count(Connection connection, String table) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }

    private Properties properties(LegacyDatabaseUrl database, String applicationName) {
        Properties properties = new Properties();
        if (database.username() != null) properties.setProperty("user", database.username());
        if (database.password() != null) properties.setProperty("password", database.password());
        properties.setProperty("ApplicationName", applicationName);
        return properties;
    }

    private record Table(String name, String columns) {}
}
