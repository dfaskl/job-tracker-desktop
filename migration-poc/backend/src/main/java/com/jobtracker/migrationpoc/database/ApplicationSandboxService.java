package com.jobtracker.migrationpoc.database;

import com.jobtracker.migrationpoc.application.ApplicationDocumentMutator;
import com.jobtracker.migrationpoc.application.ApplicationDocumentMutator.ApplicationInput;
import com.jobtracker.migrationpoc.application.ApplicationDocumentMutator.ApplicationView;
import com.jobtracker.migrationpoc.application.ApplicationDocumentMutator.Mutation;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

@Component
public class ApplicationSandboxService {
    private static final int MAX_APPLICATIONS = 500;

    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final ApplicationDocumentMutator mutator;

    public ApplicationSandboxService(
        Environment environment,
        ObjectMapper objectMapper,
        ApplicationDocumentMutator mutator
    ) {
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.mutator = mutator;
    }

    public SandboxStatus status() {
        boolean requested = Boolean.parseBoolean(environment.getProperty("POC_WRITE_ENABLED", "false"));
        boolean sharedWriteRequested = Boolean.parseBoolean(
            environment.getProperty("POC_SHARED_DATABASE_WRITE_ENABLED", "false")
        );
        String writeUrl = environment.getProperty("POC_WRITE_DATABASE_URL");
        String productionUrl = environment.getProperty("DATABASE_URL");
        if (!requested) {
            return new SandboxStatus(false, writeUrl != null && !writeUrl.isBlank(), false,
                "测试写入未开启；现有数据库保持只读");
        }
        if (writeUrl == null || writeUrl.isBlank()) {
            return new SandboxStatus(false, false, false, "尚未配置独立测试数据库");
        }
        if (productionUrl == null || productionUrl.isBlank()) {
            return new SandboxStatus(false, true, false, "无法核对生产数据库地址，写入已拒绝");
        }
        try {
            boolean isolated = !databaseIdentity(writeUrl).equals(databaseIdentity(productionUrl));
            if (isolated) return new SandboxStatus(true, true, true, "独立数据库写入已开启");
            if (sharedWriteRequested) {
                return new SandboxStatus(true, true, false, "共享生产数据库写入已开启；新旧系统数据实时一致");
            }
            return new SandboxStatus(false, true, false,
                "写入库与生产库相同；如需新旧系统共享数据，请显式开启共享写入");
        } catch (IllegalArgumentException exception) {
            return new SandboxStatus(false, true, false, "数据库地址格式无效，写入已拒绝");
        }
    }

    public ApplicationPage findApplications(String email) throws Exception {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT d.data::text FROM users u JOIN user_data d ON d.user_id=u.id "
                     + "WHERE lower(u.email)=? AND u.disabled_at IS NULL"
             )) {
            statement.setString(1, normalizeEmail(email));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SandboxDataNotFoundException("测试库中没有当前账号的数据副本");
                return mapApplications(result.getString(1));
            }
        }
    }

    public Mutation create(String email, ApplicationInput input) throws Exception {
        return mutate(email, "poc-application-create", json -> mutator.create(json, input));
    }

    public Mutation update(String email, String id, ApplicationInput input, String expectedUpdatedAt) throws Exception {
        return mutate(email, "poc-application-update", json -> mutator.update(json, id, input, expectedUpdatedAt));
    }

    public Mutation delete(String email, String id, String expectedUpdatedAt) throws Exception {
        return mutate(email, "poc-application-delete", json -> mutator.delete(json, id, expectedUpdatedAt));
    }

    private Mutation mutate(String email, String reason, DocumentChange change) throws Exception {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                UserDocument current = lockUserDocument(connection, email);
                Mutation mutation = change.apply(current.json());
                insertBackup(connection, current.userId(), current.json(), reason);
                updateDocument(connection, current.userId(), mutation.documentJson());
                pruneBackups(connection, current.userId());
                connection.commit();
                return mutation;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private UserDocument lockUserDocument(Connection connection, String email) throws Exception {
        String sql = "SELECT u.id,d.data::text FROM users u JOIN user_data d ON d.user_id=u.id "
            + "WHERE lower(u.email)=? AND u.disabled_at IS NULL FOR UPDATE OF d";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizeEmail(email));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SandboxDataNotFoundException("测试库中没有当前账号的数据副本");
                return new UserDocument(result.getLong(1), result.getString(2));
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

    private void updateDocument(Connection connection, long userId, String json) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE user_data SET data=?::jsonb,updated_at=NOW() WHERE user_id=?"
        )) {
            statement.setString(1, json);
            statement.setLong(2, userId);
            if (statement.executeUpdate() != 1) throw new SandboxDataNotFoundException("测试业务数据不存在");
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
        SandboxStatus status = status();
        if (!status.enabled()) throw new SandboxDisabledException(status.message());
        LegacyDatabaseUrl config = LegacyDatabaseUrl.parse(environment.getProperty("POC_WRITE_DATABASE_URL"));
        Properties properties = new Properties();
        if (config.username() != null) properties.setProperty("user", config.username());
        if (config.password() != null) properties.setProperty("password", config.password());
        properties.setProperty("ApplicationName", "job-tracker-migration-poc-sandbox-write");
        return PooledConnections.open(config, properties);
    }

    private ApplicationPage mapApplications(String json) throws Exception {
        JsonNode source = objectMapper.readTree(json == null ? "{}" : json).path("applications");
        if (!source.isArray()) throw new IllegalStateException("Legacy applications data is not an array");
        List<ApplicationView> applications = new ArrayList<>();
        for (JsonNode item : source) {
            applications.add(new ApplicationView(
                text(item, "id", 160), text(item, "company", 240), text(item, "position", 240),
                text(item, "city", 160), text(item, "channel", 120), text(item, "appliedDate", 40),
                text(item, "stage", 80), text(item, "status", 80), text(item, "notes", 4_000),
                text(item, "createdAt", 80), text(item, "updatedAt", 80)
            ));
        }
        applications.sort(Comparator.comparing(ApplicationView::updatedAt).reversed()
            .thenComparing(ApplicationView::id));
        int total = applications.size();
        List<ApplicationView> visible = total > MAX_APPLICATIONS
            ? List.copyOf(applications.subList(0, MAX_APPLICATIONS))
            : List.copyOf(applications);
        return new ApplicationPage(visible, total, total > MAX_APPLICATIONS);
    }

    private String text(JsonNode node, String field, int maxLength) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.isObject() || value.isArray()) return "";
        String text = value.asText("").trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String databaseIdentity(String rawUrl) {
        String jdbcUrl = LegacyDatabaseUrl.parse(rawUrl).jdbcUrl();
        int queryStart = jdbcUrl.indexOf('?');
        String identity = queryStart < 0 ? jdbcUrl : jdbcUrl.substring(0, queryStart);
        while (identity.endsWith("/")) identity = identity.substring(0, identity.length() - 1);
        return identity.toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    @FunctionalInterface
    private interface DocumentChange {
        Mutation apply(String json) throws Exception;
    }

    private record UserDocument(long userId, String json) {}

    public record SandboxStatus(boolean enabled, boolean configured, boolean isolated, String message) {}
    public record ApplicationPage(List<ApplicationView> applications, int total, boolean truncated) {}

    public static class SandboxDisabledException extends RuntimeException {
        public SandboxDisabledException(String message) { super(message); }
    }

    public static class SandboxDataNotFoundException extends RuntimeException {
        public SandboxDataNotFoundException(String message) { super(message); }
    }
}
