package com.jobtracker.migrationpoc.security;

import com.jobtracker.migrationpoc.database.ApplicationSandboxService;
import com.jobtracker.migrationpoc.database.LegacyDatabaseUrl;
import com.jobtracker.migrationpoc.database.PooledConnections;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

@Component
public class PocPersistentSessionStore {
    private static final int DEFAULT_SESSION_DAYS = 7;
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final Environment environment;
    private final ApplicationSandboxService sandboxService;
    private final SecureRandom secureRandom;

    @Autowired
    public PocPersistentSessionStore(Environment environment, ApplicationSandboxService sandboxService) {
        this(environment, sandboxService, new SecureRandom());
    }

    PocPersistentSessionStore(
        Environment environment,
        ApplicationSandboxService sandboxService,
        SecureRandom secureRandom
    ) {
        this.environment = environment;
        this.sandboxService = sandboxService;
        this.secureRandom = secureRandom;
    }

    public SessionModeStatus status() {
        boolean requested = Boolean.parseBoolean(
            environment.getProperty("POC_PERSISTENT_SESSION_ENABLED", "false")
        );
        var sandbox = sandboxService.status();
        if (!requested) {
            return new SessionModeStatus(false, false, sandbox.isolated(), sessionDays(),
                "当前使用签名 Cookie；数据库持久化会话未开启");
        }
        if (!sandbox.enabled()) {
            return new SessionModeStatus(true, false, sandbox.isolated(), sessionDays(),
                "持久化会话已请求，但独立测试数据库写入未就绪");
        }
        return new SessionModeStatus(true, true, sandbox.isolated(), sessionDays(), sandbox.isolated()
            ? "Java PostgreSQL 持久化会话已开启（独立数据库）"
            : "Java PostgreSQL 持久化会话已开启（共享数据库）");
    }

    public boolean isEnabled() {
        return status().persistent();
    }

    public Duration sessionTtl() {
        return Duration.ofDays(sessionDays());
    }

    public String issue(String email) throws Exception {
        requireEnabled();
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String token = TOKEN_ENCODER.encodeToString(random);
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                long userId = sandboxUserId(connection, email);
                try (PreparedStatement cleanup = connection.prepareStatement(
                    "DELETE FROM sessions WHERE expires_at<=NOW()"
                )) {
                    cleanup.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO sessions(token_hash,user_id,expires_at) "
                        + "VALUES(?,?,NOW()+(? || ' seconds')::interval)"
                )) {
                    insert.setString(1, tokenHash(token));
                    insert.setLong(2, userId);
                    insert.setString(3, String.valueOf(sessionTtl().toSeconds()));
                    insert.executeUpdate();
                }
                connection.commit();
                return token;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public Optional<String> verifyEmail(String token) throws Exception {
        if (!isEnabled() || !validToken(token)) return Optional.empty();
        String sql = "SELECT u.email FROM sessions s JOIN users u ON u.id=s.user_id "
            + "WHERE s.token_hash=? AND s.expires_at>NOW() AND u.disabled_at IS NULL";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tokenHash(token));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(result.getString(1)) : Optional.empty();
            }
        }
    }

    public void revoke(String token) throws Exception {
        if (!isEnabled() || !validToken(token)) return;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM sessions WHERE token_hash=?"
             )) {
            statement.setString(1, tokenHash(token));
            statement.executeUpdate();
        }
    }

    static String tokenHash(String token) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash session token", exception);
        }
    }

    private long sandboxUserId(Connection connection, String email) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT id FROM users WHERE lower(email)=? AND disabled_at IS NULL"
        )) {
            statement.setString(1, email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SessionStoreException("测试库中没有当前账号，无法创建持久化会话");
                return result.getLong(1);
            }
        }
    }

    private Connection openConnection() throws Exception {
        requireEnabled();
        LegacyDatabaseUrl config = LegacyDatabaseUrl.parse(environment.getProperty("POC_WRITE_DATABASE_URL"));
        Properties properties = new Properties();
        if (config.username() != null) properties.setProperty("user", config.username());
        if (config.password() != null) properties.setProperty("password", config.password());
        properties.setProperty("ApplicationName", "job-tracker-migration-poc-persistent-session");
        return PooledConnections.open(config, properties);
    }

    private void requireEnabled() {
        SessionModeStatus status = status();
        if (!status.persistent()) throw new SessionStoreException(status.message());
    }

    private boolean validToken(String token) {
        return token != null && !token.isBlank() && token.length() <= 512;
    }

    private int sessionDays() {
        try {
            int configured = Integer.parseInt(environment.getProperty(
                "POC_SESSION_DAYS", String.valueOf(DEFAULT_SESSION_DAYS)
            ));
            return Math.max(1, Math.min(30, configured));
        } catch (NumberFormatException exception) {
            return DEFAULT_SESSION_DAYS;
        }
    }

    public record SessionModeStatus(
        boolean requested,
        boolean persistent,
        boolean databaseIsolated,
        int sessionDays,
        String message
    ) {}

    public static class SessionStoreException extends RuntimeException {
        public SessionStoreException(String message) { super(message); }
    }
}
