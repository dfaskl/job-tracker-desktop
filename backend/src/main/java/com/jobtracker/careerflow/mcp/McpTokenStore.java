package com.jobtracker.careerflow.mcp;

import com.jobtracker.careerflow.config.AppEnvironment;
import com.jobtracker.careerflow.database.LegacyDatabaseUrl;
import com.jobtracker.careerflow.database.PooledConnections;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.*;

@Component
public class McpTokenStore {
    public static final Set<String> SCOPES = Set.of("applications:read", "applications:write", "events:read", "events:write");
    private static final String PREFIX = "cflow_mcp_";
    private final Environment environment;
    private final SecureRandom random = new SecureRandom();

    public McpTokenStore(Environment environment) { this.environment = environment; }

    public IssuedToken create(String email, String name, Integer days, List<String> scopes) throws Exception {
        String label = name == null ? "" : name.trim();
        if (label.isEmpty() || label.length() > 80) throw new IllegalArgumentException("令牌名称应为 1–80 个字符");
        int expiryDays = days == null ? 90 : days;
        if (expiryDays < 1 || expiryDays > 365) throw new IllegalArgumentException("有效期应为 1–365 天");
        if (scopes == null || scopes.isEmpty() || scopes.stream().anyMatch(scope -> scope == null || !SCOPES.contains(scope)))
            throw new IllegalArgumentException("请选择有效的访问权限");
        Set<String> allowed = new TreeSet<>(scopes);
        if (allowed.contains("applications:write")) allowed.add("applications:read");
        if (allowed.contains("events:write")) allowed.add("events:read");
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String secret = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long now = System.currentTimeMillis();
        TokenInfo token = new TokenInfo(UUID.randomUUID().toString(), label, secret.substring(0, PREFIX.length() + 8),
            List.copyOf(allowed), now, now + Duration.ofDays(expiryDays).toMillis(), null, null);
        try (Connection connection = open()) {
            long userId = userId(connection, email);
            try (var count = connection.prepareStatement("SELECT COUNT(*) FROM mcp_tokens WHERE user_id=? AND revoked_at IS NULL AND expires_at>?")) {
                count.setLong(1, userId); count.setLong(2, now);
                try (ResultSet row = count.executeQuery()) {
                    row.next();
                    if (row.getInt(1) >= 20) throw new IllegalArgumentException("最多保留 20 个有效令牌，请先撤销不用的令牌");
                }
            }
            try (var insert = connection.prepareStatement("INSERT INTO mcp_tokens(id,user_id,name,token_hash,token_prefix,scopes,created_at,expires_at) VALUES(?,?,?,?,?,?,?,?)")) {
                insert.setString(1, token.id()); insert.setLong(2, userId); insert.setString(3, label);
                insert.setString(4, hash(secret)); insert.setString(5, token.prefix());
                insert.setString(6, String.join(",", allowed)); insert.setLong(7, now); insert.setLong(8, token.expiresAt());
                insert.executeUpdate();
            }
        }
        return new IssuedToken(secret, token);
    }

    public List<TokenInfo> list(String email) throws Exception {
        try (Connection connection = open()) {
            long userId = userId(connection, email);
            try (var select = connection.prepareStatement("SELECT id,name,token_prefix,scopes,created_at,expires_at,last_used_at,revoked_at FROM mcp_tokens WHERE user_id=? ORDER BY CASE WHEN revoked_at IS NULL AND expires_at>? THEN 0 ELSE 1 END,created_at DESC LIMIT 100")) {
                select.setLong(1, userId); select.setLong(2, System.currentTimeMillis());
                List<TokenInfo> tokens = new ArrayList<>();
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) tokens.add(new TokenInfo(rows.getString("id"), rows.getString("name"), rows.getString("token_prefix"),
                        scopes(rows.getString("scopes")), rows.getLong("created_at"), rows.getLong("expires_at"),
                        nullableLong(rows, "last_used_at"), nullableLong(rows, "revoked_at")));
                }
                return tokens;
            }
        }
    }

    public boolean revoke(String email, String id) throws Exception {
        try (Connection connection = open()) {
            long userId = userId(connection, email);
            try (var update = connection.prepareStatement("UPDATE mcp_tokens SET revoked_at=? WHERE id=? AND user_id=? AND revoked_at IS NULL")) {
                update.setLong(1, System.currentTimeMillis()); update.setString(2, id); update.setLong(3, userId);
                return update.executeUpdate() == 1;
            }
        }
    }

    public Optional<Principal> authenticate(String secret) throws Exception {
        if (secret == null || !secret.matches("cflow_mcp_[A-Za-z0-9_-]{43}")) return Optional.empty();
        long now = System.currentTimeMillis();
        try (Connection connection = open()) {
            Principal principal;
            Long lastUsed;
            try (var select = connection.prepareStatement("SELECT t.id,t.user_id,t.scopes,t.last_used_at,u.email FROM mcp_tokens t JOIN users u ON u.id=t.user_id WHERE t.token_hash=? AND t.revoked_at IS NULL AND t.expires_at>? AND u.disabled_at IS NULL")) {
                select.setString(1, hash(secret)); select.setLong(2, now);
                try (ResultSet row = select.executeQuery()) {
                    if (!row.next()) return Optional.empty();
                    principal = new Principal(row.getString("id"), row.getLong("user_id"), row.getString("email"), Set.copyOf(scopes(row.getString("scopes"))));
                    lastUsed = nullableLong(row, "last_used_at");
                }
            }
            if (lastUsed == null || now - lastUsed >= 60_000) {
                try (var update = connection.prepareStatement("UPDATE mcp_tokens SET last_used_at=? WHERE id=?")) {
                    update.setLong(1, now); update.setString(2, principal.tokenId()); update.executeUpdate();
                }
                try (var update = connection.prepareStatement("UPDATE users SET last_active_at=NOW() WHERE id=?")) {
                    update.setLong(1, principal.userId()); update.executeUpdate();
                }
            }
            return Optional.of(principal);
        }
    }

    public String startAudit(Principal principal, String tool, String target) throws Exception {
        String id = UUID.randomUUID().toString();
        try (Connection connection = open(); var insert = connection.prepareStatement("INSERT INTO mcp_audit_logs(id,user_id,token_id,tool_name,target_id,outcome,created_at) VALUES(?,?,?,?,?,'started',?)")) {
            insert.setString(1, id); insert.setLong(2, principal.userId()); insert.setString(3, principal.tokenId());
            insert.setString(4, tool); insert.setString(5, target); insert.setLong(6, System.currentTimeMillis()); insert.executeUpdate();
        }
        return id;
    }

    public void finishAudit(String id, String outcome, String target) throws Exception {
        try (Connection connection = open(); var update = connection.prepareStatement("UPDATE mcp_audit_logs SET outcome=?,target_id=? WHERE id=?")) {
            update.setString(1, outcome); update.setString(2, target); update.setString(3, id); update.executeUpdate();
            try (var prune = connection.prepareStatement("DELETE FROM mcp_audit_logs WHERE user_id=(SELECT user_id FROM mcp_audit_logs WHERE id=?) AND id IN (SELECT id FROM mcp_audit_logs WHERE user_id=(SELECT user_id FROM mcp_audit_logs WHERE id=?) ORDER BY created_at DESC,id DESC LIMIT 2147483647 OFFSET 1000)")) {
                prune.setString(1, id); prune.setString(2, id); prune.executeUpdate();
            }
        }
    }

    public List<AuditInfo> audit(String email) throws Exception {
        try (Connection connection = open()) {
            long userId = userId(connection, email);
            try (var select = connection.prepareStatement("SELECT a.tool_name,a.target_id,a.outcome,a.created_at,COALESCE(t.name,'已删除令牌') AS token_name FROM mcp_audit_logs a LEFT JOIN mcp_tokens t ON t.id=a.token_id WHERE a.user_id=? ORDER BY a.created_at DESC LIMIT 20")) {
                select.setLong(1, userId);
                List<AuditInfo> rows = new ArrayList<>();
                try (ResultSet result = select.executeQuery()) {
                    while (result.next()) rows.add(new AuditInfo(result.getString("tool_name"), result.getString("target_id"),
                        result.getString("outcome"), result.getLong("created_at"), result.getString("token_name")));
                }
                return rows;
            }
        }
    }

    private List<String> scopes(String value) { return Arrays.asList(value.split(",")); }
    private Long nullableLong(ResultSet row, String column) throws Exception {
        long value = row.getLong(column); return row.wasNull() ? null : value;
    }
    private String hash(String secret) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));
    }
    private long userId(Connection connection, String email) throws Exception {
        try (var select = connection.prepareStatement("SELECT id FROM users WHERE lower(email)=? AND disabled_at IS NULL")) {
            select.setString(1, email == null ? "" : email.toLowerCase(Locale.ROOT).trim());
            try (ResultSet row = select.executeQuery()) {
                if (!row.next()) throw new IllegalArgumentException("账户不存在或已停用");
                return row.getLong(1);
            }
        }
    }
    private Connection open() throws Exception {
        LegacyDatabaseUrl config = LegacyDatabaseUrl.parse(AppEnvironment.databaseUrl(environment));
        Properties properties = new Properties();
        if (config.username() != null) properties.setProperty("user", config.username());
        if (config.password() != null) properties.setProperty("password", config.password());
        return PooledConnections.open(config, properties);
    }
    public record Principal(String tokenId, long userId, String email, Set<String> scopes) { }
    public record TokenInfo(String id, String name, String prefix, List<String> scopes, long createdAt, long expiresAt, Long lastUsedAt, Long revokedAt) { }
    public record IssuedToken(String token, TokenInfo metadata) { }
    public record AuditInfo(String tool, String targetId, String outcome, long createdAt, String tokenName) { }
}
