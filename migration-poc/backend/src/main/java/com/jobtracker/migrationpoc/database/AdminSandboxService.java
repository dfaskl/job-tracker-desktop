package com.jobtracker.migrationpoc.database;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

@Component
public class AdminSandboxService {
    private static final int MAX_USERS = 500;
    private static final int MAX_APPLICATIONS = 500;

    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final ApplicationSandboxService applicationSandboxService;

    public AdminSandboxService(
        Environment environment,
        ObjectMapper objectMapper,
        ApplicationSandboxService applicationSandboxService
    ) {
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.applicationSandboxService = applicationSandboxService;
    }

    public AdminStatus status() {
        boolean requested = Boolean.parseBoolean(environment.getProperty("POC_ADMIN_ENABLED", "false"));
        ApplicationSandboxService.SandboxStatus sandbox = applicationSandboxService.status();
        if (!requested) {
            return new AdminStatus(false, false, sandbox.enabled(), "管理员迁移功能未开启");
        }
        if (!sandbox.enabled()) {
            return new AdminStatus(false, true, false, sandbox.message());
        }
        return new AdminStatus(true, true, true, "独立测试数据库管理员功能已开启");
    }

    public Overview overview(String adminEmail) throws Exception {
        try (Connection connection = openConnection()) {
            AdminIdentity admin = requireAdmin(connection, adminEmail);
            List<UserView> users = users(connection);
            SummaryCounts counts = summaryCounts(connection);
            return new Overview(
                new CurrentAdmin(String.valueOf(admin.id()), admin.email()),
                new Summary(
                    counts.totalUsers(), counts.enabledUsers(), counts.totalApplications(),
                    counts.activeSessions(), counts.configuredApiKeys(), registrationIsOpen(connection),
                    configured("REGISTRATION_CODE"), configured("ADMIN_EMAIL")
                ),
                users,
                counts.totalUsers() > users.size(),
                audit(connection)
            );
        }
    }

    public UserDetails details(String adminEmail, long targetId) throws Exception {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                AdminIdentity admin = requireAdmin(connection, adminEmail);
                UserDocument target = userDocument(connection, targetId);
                UserDetails details = mapDetails(target.id(), target.email(), target.json());
                insertAudit(connection, admin.id(), target.id(), target.email(), "view-user-details");
                connection.commit();
                return details;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public RegistrationResult setRegistration(String adminEmail, boolean enabled) throws Exception {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                AdminIdentity admin = requireAdmin(connection, adminEmail);
                try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO system_settings(key,value,updated_by) "
                        + "VALUES('registration_open',to_jsonb(CAST(? AS boolean)),?) "
                        + "ON CONFLICT(key) DO UPDATE SET value=EXCLUDED.value,updated_by=EXCLUDED.updated_by,updated_at=NOW()"
                )) {
                    statement.setBoolean(1, enabled);
                    statement.setLong(2, admin.id());
                    statement.executeUpdate();
                }
                insertAudit(connection, admin.id(), null, "系统注册入口",
                    enabled ? "open-registration" : "close-registration");
                connection.commit();
                return new RegistrationResult(true, enabled);
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public DisabledResult setDisabled(String adminEmail, long targetId, boolean disabled) throws Exception {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                AdminIdentity admin = requireAdmin(connection, adminEmail);
                TargetUser target = lockTarget(connection, targetId);
                rejectProtectedAdmin(admin, target, disabled ? "停用" : "修改");
                try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE users SET disabled_at=CASE WHEN ? THEN NOW() ELSE NULL END WHERE id=?"
                )) {
                    statement.setBoolean(1, disabled);
                    statement.setLong(2, target.id());
                    statement.executeUpdate();
                }
                if (disabled) {
                    try (PreparedStatement statement = connection.prepareStatement("DELETE FROM sessions WHERE user_id=?")) {
                        statement.setLong(1, target.id());
                        statement.executeUpdate();
                    }
                }
                insertAudit(connection, admin.id(), target.id(), target.email(),
                    disabled ? "disable-user" : "enable-user");
                connection.commit();
                return new DisabledResult(true, disabled);
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public DeleteResult deleteUser(String adminEmail, long targetId, String confirmEmail) throws Exception {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                AdminIdentity admin = requireAdmin(connection, adminEmail);
                TargetUser target = lockTarget(connection, targetId);
                rejectProtectedAdmin(admin, target, "删除");
                if (!normalizeEmail(confirmEmail).equals(target.email())) {
                    throw new AdminValidationException("确认邮箱不匹配");
                }
                insertAudit(connection, admin.id(), target.id(), target.email(), "delete-user");
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM users WHERE id=?")) {
                    statement.setLong(1, target.id());
                    if (statement.executeUpdate() != 1) throw new AdminNotFoundException("用户不存在");
                }
                connection.commit();
                return new DeleteResult(true);
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    UserDetails mapDetails(long userId, String email, String json) throws Exception {
        JsonNode root = objectMapper.readTree(json == null || json.isBlank()
            ? "{\"applications\":[],\"events\":[]}" : json);
        JsonNode applicationNodes = root.path("applications");
        JsonNode eventNodes = root.path("events");
        if (!applicationNodes.isArray() || !eventNodes.isArray()) {
            throw new AdminValidationException("用户业务数据结构不兼容");
        }

        Map<String, List<FlowStep>> eventFlow = new HashMap<>();
        for (JsonNode event : eventNodes) {
            String applicationId = text(event, "applicationId", 160);
            if (applicationId.isBlank()) continue;
            String at = firstText(event, 40, "completedAt", "endsAt", "startsAt", "createdAt");
            String type = text(event, "type", 100);
            String title = text(event, "title", 300);
            String result = event.path("missed").asBoolean(false)
                ? "已错过"
                : event.path("completed").asBoolean(false) ? firstText(event, 100, "result", "status") : "待完成";
            if (result.isBlank()) result = "已完成";
            String label = (type.isBlank() ? "" : type + " · ")
                + (title.isBlank() ? "日程" : title) + " · " + result;
            eventFlow.computeIfAbsent(applicationId, ignored -> new ArrayList<>()).add(new FlowStep(at, label));
        }

        List<AdminApplication> applications = new ArrayList<>();
        int total = applicationNodes.size();
        int visible = Math.min(total, MAX_APPLICATIONS);
        for (int index = 0; index < visible; index++) {
            JsonNode item = applicationNodes.get(index);
            String id = text(item, "id", 160);
            List<FlowStep> flow = new ArrayList<>();
            flow.add(new FlowStep(firstText(item, 40, "appliedDate", "createdAt"), "已投递"));
            JsonNode timeline = item.path("timeline");
            if (timeline.isArray()) {
                for (JsonNode step : timeline) {
                    flow.add(new FlowStep(text(step, "at", 40), text(step, "title", 500)));
                }
            }
            flow.addAll(eventFlow.getOrDefault(id, List.of()));
            flow.removeIf(step -> step.at().isBlank() && step.title().isBlank());
            flow.sort(Comparator.comparing(FlowStep::at));
            applications.add(new AdminApplication(
                id, text(item, "company", 300), text(item, "position", 300),
                text(item, "stage", 100), text(item, "status", 100),
                text(item, "appliedDate", 40), text(item, "city", 200),
                text(item, "channel", 100), List.copyOf(flow)
            ));
        }
        return new UserDetails(
            new DetailUser(String.valueOf(userId), email), List.copyOf(applications), total, total > visible
        );
    }

    private List<UserView> users(Connection connection) throws Exception {
        String sql = "SELECT u.id,u.email,u.is_admin,u.disabled_at,u.created_at,"
            + "CASE WHEN jsonb_typeof(d.data->'applications')='array' THEN jsonb_array_length(d.data->'applications') ELSE 0 END AS application_count,"
            + "CASE WHEN jsonb_typeof(d.data->'events')='array' THEN jsonb_array_length(d.data->'events') ELSE 0 END AS event_count,"
            + "(c.encrypted_api_key IS NOT NULL) AS has_api_key,"
            + "(SELECT MAX(s.created_at) FROM sessions s WHERE s.user_id=u.id) AS last_login_at "
            + "FROM users u LEFT JOIN user_data d ON d.user_id=u.id LEFT JOIN api_configs c ON c.user_id=u.id "
            + "ORDER BY u.is_admin DESC,u.created_at ASC LIMIT ?";
        List<UserView> users = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, MAX_USERS);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    users.add(new UserView(
                        String.valueOf(result.getLong("id")), result.getString("email"),
                        result.getBoolean("is_admin"), result.getObject("disabled_at") != null,
                        string(result.getObject("disabled_at")), string(result.getObject("created_at")),
                        string(result.getObject("last_login_at")), result.getInt("application_count"),
                        result.getInt("event_count"), result.getBoolean("has_api_key")
                    ));
                }
            }
        }
        return List.copyOf(users);
    }

    private SummaryCounts summaryCounts(Connection connection) throws Exception {
        String sql = "SELECT COUNT(*) AS total_users,COUNT(*) FILTER (WHERE disabled_at IS NULL) AS enabled_users,"
            + "COALESCE(SUM(CASE WHEN jsonb_typeof(d.data->'applications')='array' THEN jsonb_array_length(d.data->'applications') ELSE 0 END),0) AS total_applications,"
            + "(SELECT COUNT(*) FROM sessions WHERE expires_at>NOW()) AS active_sessions,"
            + "(SELECT COUNT(*) FROM api_configs WHERE encrypted_api_key IS NOT NULL) AS configured_api_keys "
            + "FROM users u LEFT JOIN user_data d ON d.user_id=u.id";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return new SummaryCounts(
                result.getInt("total_users"), result.getInt("enabled_users"),
                result.getInt("total_applications"), result.getInt("active_sessions"),
                result.getInt("configured_api_keys")
            );
        }
    }

    private List<AuditView> audit(Connection connection) throws Exception {
        List<AuditView> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT id,action,target_email,created_at FROM admin_audit_logs ORDER BY created_at DESC LIMIT 30"
        ); ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                items.add(new AuditView(
                    String.valueOf(result.getLong("id")), result.getString("action"),
                    result.getString("target_email"), string(result.getObject("created_at"))
                ));
            }
        }
        return List.copyOf(items);
    }

    private boolean registrationIsOpen(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT value::text FROM system_settings WHERE key='registration_open'"
        ); ResultSet result = statement.executeQuery()) {
            if (result.next()) return Boolean.parseBoolean(result.getString(1));
        }
        return !"false".equalsIgnoreCase(environment.getProperty("ALLOW_REGISTRATION", "true"));
    }

    private AdminIdentity requireAdmin(Connection connection, String email) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT id,email,is_admin FROM users WHERE lower(email)=? AND disabled_at IS NULL"
        )) {
            statement.setString(1, normalizeEmail(email));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !result.getBoolean("is_admin")) {
                    throw new AdminForbiddenException("需要独立测试数据库的管理员权限");
                }
                return new AdminIdentity(result.getLong("id"), result.getString("email"));
            }
        }
    }

    private UserDocument userDocument(Connection connection, long targetId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT u.id,u.email,d.data::text FROM users u LEFT JOIN user_data d ON d.user_id=u.id WHERE u.id=?"
        )) {
            statement.setLong(1, targetId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new AdminNotFoundException("用户不存在");
                return new UserDocument(result.getLong("id"), result.getString("email"), result.getString(3));
            }
        }
    }

    private TargetUser lockTarget(Connection connection, long targetId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT id,email,is_admin FROM users WHERE id=? FOR UPDATE"
        )) {
            statement.setLong(1, targetId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new AdminNotFoundException("用户不存在");
                return new TargetUser(result.getLong("id"), result.getString("email"), result.getBoolean("is_admin"));
            }
        }
    }

    private void rejectProtectedAdmin(AdminIdentity admin, TargetUser target, String action) {
        if (target.id() == admin.id()) throw new AdminValidationException("不能" + action + "自己的管理员账号");
        if (target.admin()) throw new AdminValidationException("不能通过迁移后台" + action + "管理员账号");
    }

    private void insertAudit(
        Connection connection,
        long adminId,
        Long targetId,
        String targetEmail,
        String action
    ) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO admin_audit_logs(admin_user_id,target_user_id,target_email,action) VALUES(?,?,?,?)"
        )) {
            statement.setLong(1, adminId);
            if (targetId == null) statement.setNull(2, java.sql.Types.BIGINT);
            else statement.setLong(2, targetId);
            statement.setString(3, targetEmail);
            statement.setString(4, action);
            statement.executeUpdate();
        }
    }

    private Connection openConnection() throws Exception {
        AdminStatus status = status();
        if (!status.enabled()) throw new AdminDisabledException(status.message());
        LegacyDatabaseUrl config = LegacyDatabaseUrl.parse(environment.getProperty("POC_WRITE_DATABASE_URL"));
        Properties properties = new Properties();
        if (config.username() != null) properties.setProperty("user", config.username());
        if (config.password() != null) properties.setProperty("password", config.password());
        properties.setProperty("ApplicationName", "job-tracker-migration-poc-admin-sandbox");
        return DriverManager.getConnection(config.jdbcUrl(), properties);
    }

    private boolean configured(String key) {
        String value = environment.getProperty(key);
        return value != null && !value.isBlank();
    }

    private String firstText(JsonNode node, int maxLength, String... fields) {
        for (String field : fields) {
            String value = text(node, field, maxLength);
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private String text(JsonNode node, String field, int maxLength) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.isObject() || value.isArray()) return "";
        String result = value.asText("").trim();
        return result.length() <= maxLength ? result : result.substring(0, maxLength);
    }

    private String normalizeEmail(String value) {
        String email = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return email.length() <= 254 ? email : email.substring(0, 254);
    }

    private String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private record AdminIdentity(long id, String email) {}
    private record TargetUser(long id, String email, boolean admin) {}
    private record UserDocument(long id, String email, String json) {}
    private record SummaryCounts(int totalUsers, int enabledUsers, int totalApplications, int activeSessions,
                                 int configuredApiKeys) {}

    public record AdminStatus(boolean enabled, boolean requested, boolean sandboxEnabled, String message) {}
    public record CurrentAdmin(String id, String email) {}
    public record Summary(int totalUsers, int enabledUsers, int totalApplications, int activeSessions,
                          int configuredApiKeys, boolean registrationOpen, boolean registrationCodeEnabled,
                          boolean adminEmailConfigured) {}
    public record UserView(String id, String email, boolean isAdmin, boolean disabled, String disabledAt,
                           String createdAt, String lastLoginAt, int applicationCount, int eventCount,
                           boolean hasApiKey) {}
    public record AuditView(String id, String action, String targetEmail, String createdAt) {}
    public record Overview(CurrentAdmin currentUser, Summary summary, List<UserView> users,
                           boolean usersTruncated, List<AuditView> audit) {}
    public record FlowStep(String at, String title) {}
    public record AdminApplication(String id, String company, String position, String stage, String status,
                                   String appliedDate, String city, String channel, List<FlowStep> flow) {}
    public record DetailUser(String id, String email) {}
    public record UserDetails(DetailUser user, List<AdminApplication> applications,
                              int totalApplications, boolean truncated) {}
    public record RegistrationResult(boolean ok, boolean registrationOpen) {}
    public record DisabledResult(boolean ok, boolean disabled) {}
    public record DeleteResult(boolean ok) {}

    public static class AdminDisabledException extends RuntimeException {
        public AdminDisabledException(String message) { super(message); }
    }

    public static class AdminForbiddenException extends RuntimeException {
        public AdminForbiddenException(String message) { super(message); }
    }

    public static class AdminNotFoundException extends RuntimeException {
        public AdminNotFoundException(String message) { super(message); }
    }

    public static class AdminValidationException extends RuntimeException {
        public AdminValidationException(String message) { super(message); }
    }
}
