package com.jobtracker.migrationpoc.database;

import com.jobtracker.migrationpoc.backup.BackupDocumentValidator;
import com.jobtracker.migrationpoc.backup.BackupDocumentValidator.BackupSummary;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

@Component
public class BackupSandboxService {
    private final Environment environment;
    private final ApplicationSandboxService applicationSandboxService;
    private final BackupDocumentValidator validator;

    public BackupSandboxService(
        Environment environment,
        ApplicationSandboxService applicationSandboxService,
        BackupDocumentValidator validator
    ) {
        this.environment = environment;
        this.applicationSandboxService = applicationSandboxService;
        this.validator = validator;
    }

    public ApplicationSandboxService.SandboxStatus status() {
        return applicationSandboxService.status();
    }

    public BackupPage backups(String email) throws Exception {
        try (Connection connection = openConnection()) {
            UserDocument current = currentUserDocument(connection, email, false);
            List<BackupItem> items = new ArrayList<>();
            String sql = "SELECT id,reason,created_at,octet_length(data::text) AS size,"
                + "CASE WHEN jsonb_typeof(data->'applications')='array' THEN jsonb_array_length(data->'applications') ELSE 0 END AS application_count,"
                + "CASE WHEN jsonb_typeof(data->'events')='array' THEN jsonb_array_length(data->'events') ELSE 0 END AS event_count "
                + "FROM data_backups WHERE user_id=? ORDER BY created_at DESC LIMIT 30";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, current.userId());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        items.add(new BackupItem(
                            result.getLong("id"), result.getString("reason"), instant(result, "created_at"),
                            result.getLong("size"), result.getInt("application_count"), result.getInt("event_count")
                        ));
                    }
                }
            }
            return new BackupPage(List.copyOf(items), current.updatedAt());
        }
    }

    public RestoreResult restore(String email, long backupId, String expectedCurrentUpdatedAt) throws Exception {
        if (backupId <= 0) throw new BackupNotFoundException("备份不存在");
        if (expectedCurrentUpdatedAt == null || expectedCurrentUpdatedAt.isBlank()) {
            throw new BackupConflictException("缺少当前数据版本，请刷新备份列表后重试");
        }
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                UserDocument current = currentUserDocument(connection, email, true);
                if (!expectedCurrentUpdatedAt.equals(current.updatedAt())) {
                    throw new BackupConflictException("当前数据已发生变化，请刷新备份列表后重试");
                }
                String backupJson = backupJson(connection, current.userId(), backupId);
                BackupSummary summary = validator.validate(backupJson);
                insertBackup(connection, current.userId(), current.json(), "before-restore");
                String updatedAt = replaceDocument(connection, current.userId(), backupJson);
                pruneBackups(connection, current.userId());
                connection.commit();
                return new RestoreResult(backupId, summary.applicationCount(), summary.eventCount(), updatedAt);
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private UserDocument currentUserDocument(Connection connection, String email, boolean lock) throws Exception {
        String sql = "SELECT u.id,d.data::text,d.updated_at FROM users u JOIN user_data d ON d.user_id=u.id "
            + "WHERE lower(u.email)=? AND u.disabled_at IS NULL" + (lock ? " FOR UPDATE OF d" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizeEmail(email));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SandboxDataNotFoundException("测试库中没有当前账号的数据副本");
                return new UserDocument(result.getLong(1), result.getString(2), instant(result, "updated_at"));
            }
        }
    }

    private String backupJson(Connection connection, long userId, long backupId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT data::text FROM data_backups WHERE id=? AND user_id=?"
        )) {
            statement.setLong(1, backupId);
            statement.setLong(2, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new BackupNotFoundException("备份不存在");
                return result.getString(1);
            }
        }
    }

    private void insertBackup(Connection connection, long userId, String json, String reason) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO data_backups(user_id,data,reason) VALUES(?,?::jsonb,?)"
        )) {
            statement.setLong(1, userId);
            statement.setString(2, json);
            statement.setString(3, reason);
            statement.executeUpdate();
        }
    }

    private String replaceDocument(Connection connection, long userId, String json) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE user_data SET data=?::jsonb,updated_at=NOW() WHERE user_id=? RETURNING updated_at"
        )) {
            statement.setString(1, json);
            statement.setLong(2, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SandboxDataNotFoundException("测试业务数据不存在");
                return instant(result, "updated_at");
            }
        }
    }

    private void pruneBackups(Connection connection, long userId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "DELETE FROM data_backups WHERE user_id=? AND id NOT IN "
                + "(SELECT id FROM data_backups WHERE user_id=? ORDER BY created_at DESC LIMIT 30)"
        )) {
            statement.setLong(1, userId);
            statement.setLong(2, userId);
            statement.executeUpdate();
        }
    }

    private Connection openConnection() throws Exception {
        var status = status();
        if (!status.enabled()) throw new SandboxDisabledException(status.message());
        LegacyDatabaseUrl config = LegacyDatabaseUrl.parse(environment.getProperty("POC_WRITE_DATABASE_URL"));
        Properties properties = new Properties();
        if (config.username() != null) properties.setProperty("user", config.username());
        if (config.password() != null) properties.setProperty("password", config.password());
        properties.setProperty("ApplicationName", "job-tracker-migration-poc-backup-sandbox");
        return DriverManager.getConnection(config.jdbcUrl(), properties);
    }

    private String instant(ResultSet result, String column) throws Exception {
        return result.getObject(column, OffsetDateTime.class).toInstant().toString();
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private record UserDocument(long userId, String json, String updatedAt) {}

    public record BackupItem(
        long id,
        String reason,
        String createdAt,
        long size,
        int applicationCount,
        int eventCount
    ) {}

    public record BackupPage(List<BackupItem> items, String currentUpdatedAt) {}
    public record RestoreResult(long backupId, int applicationCount, int eventCount, String currentUpdatedAt) {}

    public static class SandboxDisabledException extends RuntimeException {
        public SandboxDisabledException(String message) { super(message); }
    }

    public static class SandboxDataNotFoundException extends RuntimeException {
        public SandboxDataNotFoundException(String message) { super(message); }
    }

    public static class BackupNotFoundException extends RuntimeException {
        public BackupNotFoundException(String message) { super(message); }
    }

    public static class BackupConflictException extends RuntimeException {
        public BackupConflictException(String message) { super(message); }
    }
}
