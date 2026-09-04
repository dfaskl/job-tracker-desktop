package com.jobtracker.migrationpoc.database;

import com.jobtracker.migrationpoc.event.EventDocumentMutator;
import com.jobtracker.migrationpoc.event.EventDocumentMutator.EventInput;
import com.jobtracker.migrationpoc.event.EventDocumentMutator.EventPage;
import com.jobtracker.migrationpoc.event.EventDocumentMutator.Mutation;
import com.jobtracker.migrationpoc.event.EventDocumentMutator.Resolution;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.Properties;

@Component
public class EventSandboxService {
    private static final int MAX_EVENTS = 1_000;

    private final Environment environment;
    private final ApplicationSandboxService applicationSandboxService;
    private final EventDocumentMutator mutator;

    public EventSandboxService(
        Environment environment,
        ApplicationSandboxService applicationSandboxService,
        EventDocumentMutator mutator
    ) {
        this.environment = environment;
        this.applicationSandboxService = applicationSandboxService;
        this.mutator = mutator;
    }

    public ApplicationSandboxService.SandboxStatus status() {
        return applicationSandboxService.status();
    }

    public EventPage findEvents(String email) throws Exception {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT d.data::text FROM users u JOIN user_data d ON d.user_id=u.id "
                     + "WHERE lower(u.email)=? AND u.disabled_at IS NULL"
             )) {
            statement.setString(1, normalizeEmail(email));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SandboxDataNotFoundException("测试库中没有当前账号的数据副本");
                return mutator.page(result.getString(1), MAX_EVENTS);
            }
        }
    }

    public Mutation create(String email, EventInput input) throws Exception {
        return mutate(email, "poc-event-create", json -> mutator.create(json, input));
    }

    public Mutation update(String email, String id, EventInput input, String expectedUpdatedAt) throws Exception {
        return mutate(email, "poc-event-update", json -> mutator.update(json, id, input, expectedUpdatedAt));
    }

    public Mutation resolve(String email, String id, Resolution resolution, String expectedUpdatedAt) throws Exception {
        return mutate(email, "poc-event-" + resolution.name().toLowerCase(Locale.ROOT),
            json -> mutator.resolve(json, id, resolution, expectedUpdatedAt));
    }

    public Mutation delete(String email, String id, String expectedUpdatedAt) throws Exception {
        return mutate(email, "poc-event-delete", json -> mutator.delete(json, id, expectedUpdatedAt));
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
        makeRoomForBackup(connection, userId);
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

    private void makeRoomForBackup(Connection connection, long userId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "DELETE FROM data_backups WHERE user_id=? AND id IN "
                + "(SELECT id FROM data_backups WHERE user_id=? ORDER BY created_at DESC,id DESC OFFSET 29)"
        )) {
            statement.setLong(1, userId);
            statement.setLong(2, userId);
            statement.executeUpdate();
        }
    }

    private void pruneBackups(Connection connection, long userId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "DELETE FROM data_backups WHERE user_id=? AND id NOT IN "
                + "(SELECT id FROM data_backups WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT 30)"
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
        properties.setProperty("ApplicationName", "job-tracker-migration-poc-event-sandbox");
        return PooledConnections.open(config, properties);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    @FunctionalInterface
    private interface DocumentChange {
        Mutation apply(String json) throws Exception;
    }

    private record UserDocument(long userId, String json) {}

    public static class SandboxDisabledException extends RuntimeException {
        public SandboxDisabledException(String message) { super(message); }
    }

    public static class SandboxDataNotFoundException extends RuntimeException {
        public SandboxDataNotFoundException(String message) { super(message); }
    }
}
