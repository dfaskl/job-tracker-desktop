package com.jobtracker.migrationpoc.database;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Component
public class LegacyDatabaseProbe {
    private static final List<String> EXPECTED_TABLES = List.of(
        "users", "sessions", "user_data", "company_links", "api_configs",
        "data_backups", "admin_audit_logs", "system_settings"
    );

    private final Environment environment;
    private final ObjectMapper objectMapper;

    public LegacyDatabaseProbe(Environment environment, ObjectMapper objectMapper) {
        this.environment = environment;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return hasText(environment.getProperty("DATABASE_URL"));
    }

    public boolean isProtected() {
        return hasText(environment.getProperty("POC_ACCESS_TOKEN"));
    }

    public boolean isAuthorized(String candidate) {
        String expected = environment.getProperty("POC_ACCESS_TOKEN");
        return hasText(expected) && expected.equals(candidate);
    }

    public DatabaseProbeResult probe() {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (!hasText(databaseUrl)) return DatabaseProbeResult.notConfigured();

        try {
            LegacyDatabaseUrl config = LegacyDatabaseUrl.parse(databaseUrl);
            Properties properties = new Properties();
            if (config.username() != null) properties.setProperty("user", config.username());
            if (config.password() != null) properties.setProperty("password", config.password());
            properties.setProperty("ApplicationName", "job-tracker-migration-poc");

            try (Connection connection = PooledConnections.open(config, properties)) {
                connection.setReadOnly(true);
                List<String> missingTables = findMissingTables(connection);
                SampleResult sample = missingTables.contains("user_data")
                    ? new SampleResult(false, false)
                    : inspectBusinessJson(connection);
                return new DatabaseProbeResult(
                    true,
                    true,
                    missingTables.isEmpty(),
                    missingTables,
                    sample.present(),
                    sample.compatible(),
                    missingTables.isEmpty() ? "只读兼容性检查完成" : "数据库缺少预期表"
                );
            }
        } catch (Exception exception) {
            return DatabaseProbeResult.failed("数据库只读检查失败：" + exception.getClass().getSimpleName());
        }
    }

    private List<String> findMissingTables(Connection connection) throws Exception {
        List<String> missing = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
            for (String table : EXPECTED_TABLES) {
                statement.setString(1, "public." + table);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next() || !result.getBoolean(1)) missing.add(table);
                }
            }
        }
        return missing;
    }

    private SampleResult inspectBusinessJson(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT data::text FROM user_data ORDER BY updated_at DESC LIMIT 1"
        ); ResultSet result = statement.executeQuery()) {
            if (!result.next()) return new SampleResult(false, true);
            JsonNode data = objectMapper.readTree(result.getString(1));
            return new SampleResult(true, data.path("applications").isArray() && data.path("events").isArray());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record SampleResult(boolean present, boolean compatible) {}
}
