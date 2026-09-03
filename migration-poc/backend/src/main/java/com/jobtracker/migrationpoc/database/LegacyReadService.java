package com.jobtracker.migrationpoc.database;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

@Component
public class LegacyReadService {
    private static final int MAX_APPLICATIONS = 500;

    private final Environment environment;
    private final ObjectMapper objectMapper;

    public LegacyReadService(Environment environment, ObjectMapper objectMapper) {
        this.environment = environment;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        return databaseUrl != null && !databaseUrl.isBlank();
    }

    public Optional<LegacyUser> findUserByEmail(String email) throws Exception {
        String sql = "SELECT id,email,password_salt,password_hash,disabled_at IS NOT NULL AS disabled FROM users WHERE email=?";
        try (Connection connection = openReadOnlyConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(user(result)) : Optional.empty();
            }
        }
    }

    public Optional<LegacyUser> findUserById(long userId) throws Exception {
        String sql = "SELECT id,email,password_salt,password_hash,disabled_at IS NOT NULL AS disabled FROM users WHERE id=?";
        try (Connection connection = openReadOnlyConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(user(result)) : Optional.empty();
            }
        }
    }

    public ApplicationPage findApplications(long userId) throws Exception {
        String sql = "SELECT data::text FROM user_data WHERE user_id=?";
        try (Connection connection = openReadOnlyConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return new ApplicationPage(List.of(), 0, false);
                return mapApplications(result.getString(1));
            }
        }
    }

    public Optional<JsonNode> findBusinessData(long userId) throws Exception {
        String sql = "SELECT data::text FROM user_data WHERE user_id=?";
        try (Connection connection = openReadOnlyConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(mapBusinessData(result.getString(1)));
            }
        }
    }

    JsonNode mapBusinessData(String json) throws Exception {
        JsonNode parsed = objectMapper.readTree(json == null ? "{}" : json);
        if (!(parsed instanceof ObjectNode root)) {
            throw new IllegalStateException("Legacy business data is not an object");
        }
        if (!root.path("applications").isArray()) {
            throw new IllegalStateException("Legacy applications data is not an array");
        }
        if (!root.path("events").isArray()) {
            throw new IllegalStateException("Legacy events data is not an array");
        }
        ObjectNode safe = root.deepCopy();
        JsonNode settings = safe.path("settings");
        if (settings instanceof ObjectNode settingsObject) settingsObject.remove("apiKey");
        return safe;
    }

    ApplicationPage mapApplications(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json == null ? "{}" : json);
        JsonNode source = root.path("applications");
        if (!source.isArray()) throw new IllegalStateException("Legacy applications data is not an array");

        List<ApplicationSummary> applications = new ArrayList<>();
        for (JsonNode item : source) {
            applications.add(new ApplicationSummary(
                text(item, "id", 160),
                text(item, "company", 240),
                text(item, "position", 240),
                text(item, "city", 160),
                text(item, "channel", 120),
                text(item, "appliedDate", 40),
                text(item, "stage", 80),
                text(item, "status", 80),
                text(item, "updatedAt", 80)
            ));
        }
        applications.sort(Comparator.comparing(ApplicationSummary::updatedAt).reversed()
            .thenComparing(ApplicationSummary::id));
        int total = applications.size();
        List<ApplicationSummary> visible = total > MAX_APPLICATIONS
            ? List.copyOf(applications.subList(0, MAX_APPLICATIONS))
            : List.copyOf(applications);
        return new ApplicationPage(visible, total, total > MAX_APPLICATIONS);
    }

    private Connection openReadOnlyConnection() throws Exception {
        LegacyDatabaseUrl config = LegacyDatabaseUrl.parse(environment.getProperty("DATABASE_URL"));
        Properties properties = new Properties();
        if (config.username() != null) properties.setProperty("user", config.username());
        if (config.password() != null) properties.setProperty("password", config.password());
        properties.setProperty("ApplicationName", "job-tracker-migration-poc-readonly");
        Connection connection = DriverManager.getConnection(config.jdbcUrl(), properties);
        connection.setReadOnly(true);
        return connection;
    }

    private LegacyUser user(ResultSet result) throws Exception {
        return new LegacyUser(
            result.getLong("id"),
            result.getString("email"),
            result.getString("password_salt"),
            result.getString("password_hash"),
            result.getBoolean("disabled")
        );
    }

    private String text(JsonNode item, String name, int maxLength) {
        JsonNode value = item.path(name);
        if (value.isMissingNode() || value.isNull() || value.isObject() || value.isArray()) return "";
        String text = value.asText("").trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    public record LegacyUser(long id, String email, String passwordSalt, String passwordHash, boolean disabled) {}

    public record ApplicationSummary(
        String id,
        String company,
        String position,
        String city,
        String channel,
        String appliedDate,
        String stage,
        String status,
        String updatedAt
    ) {}

    public record ApplicationPage(List<ApplicationSummary> applications, int total, boolean truncated) {}
}
