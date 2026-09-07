package com.jobtracker.migrationpoc.config;

import com.jobtracker.migrationpoc.database.LegacyDatabaseUrl;
import com.jobtracker.migrationpoc.database.PooledConnections;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Properties;

@Component
public class DatabaseSchemaInitializer implements ApplicationRunner {
    private final Environment environment;

    public DatabaseSchemaInitializer(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String databaseUrl = AppEnvironment.databaseUrl(environment);
        if (databaseUrl == null) return;
        LegacyDatabaseUrl config = LegacyDatabaseUrl.parse(databaseUrl);
        Properties properties = new Properties();
        if (config.username() != null) properties.setProperty("user", config.username());
        if (config.password() != null) properties.setProperty("password", config.password());
        properties.setProperty("ApplicationName", "job-tracker-schema");
        String sql = new ClassPathResource("schema.sql").getContentAsString(StandardCharsets.UTF_8);
        try (Connection connection = PooledConnections.open(config, properties);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
            statement.executeUpdate("DELETE FROM sessions WHERE expires_at <= NOW()");
            String adminEmail = environment.getProperty("ADMIN_EMAIL", "").trim().toLowerCase();
            if (!adminEmail.isBlank()) {
                try (var update = connection.prepareStatement("UPDATE users SET is_admin=TRUE WHERE lower(email)=?")) {
                    update.setString(1, adminEmail);
                    update.executeUpdate();
                }
            }
        }
    }
}