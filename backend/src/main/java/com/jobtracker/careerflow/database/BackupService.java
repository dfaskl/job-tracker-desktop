package com.jobtracker.careerflow.database;

import com.jobtracker.careerflow.backup.BackupDocumentValidator;
import com.jobtracker.careerflow.backup.BackupDocumentValidator.BackupSummary;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@Component
public class BackupService {
    private static final String SALARY_ESTIMATE_VERSION = "salary-v5";
    private static final Duration SALARY_RETRY_DELAY = Duration.ofHours(6);
    private final Environment environment;
    private final ApplicationService applicationSandboxService;
    private final BackupDocumentValidator validator;
    private final ObjectMapper objectMapper;

    public BackupService(
        Environment environment,
        ApplicationService applicationSandboxService,
        BackupDocumentValidator validator,
        ObjectMapper objectMapper
    ) {
        this.environment = environment;
        this.applicationSandboxService = applicationSandboxService;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    public ApplicationService.SandboxStatus status() {
        return applicationSandboxService.status();
    }

    public JsonNode businessData(String email) throws Exception {
        try (Connection connection = openConnection()) {
            UserDocument current = currentUserDocument(connection, email, false);
            JsonNode data = objectMapper.readTree(current.json());
            if (!data.isObject() || !data.path("applications").isArray() || !data.path("events").isArray()) {
                throw new SandboxDataNotFoundException("测试业务数据结构不兼容");
            }
            if (data.has("settings") && data.path("settings").isObject()) {
                ((tools.jackson.databind.node.ObjectNode) data.path("settings")).remove("apiKey");
            }
            return data;
        }
    }
    public CompanyLinks companyLinks(String email) throws Exception {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
            "SELECT COALESCE(c.items,'[]'::jsonb)::text,c.updated_at FROM users u LEFT JOIN company_links c ON c.user_id=u.id WHERE lower(u.email)=? AND u.disabled_at IS NULL"
        )) {
            statement.setString(1, normalizeEmail(email));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SandboxDataNotFoundException("测试库中没有当前账号");
                return new CompanyLinks(objectMapper.readTree(result.getString(1)),
                    result.getObject(2) == null ? "" : instant(result, "updated_at"));
            }
        }
    }

    public CompanyLinks saveCompanyLinks(String email, JsonNode source) throws Exception {
        if (source == null || !source.isArray() || source.size() > 500) throw new IllegalArgumentException("官网链接数据格式无效");
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                long userId;
                try (PreparedStatement user = connection.prepareStatement("SELECT id FROM users WHERE lower(email)=? AND disabled_at IS NULL FOR UPDATE")) {
                    user.setString(1, normalizeEmail(email));
                    try (ResultSet result = user.executeQuery()) {
                        if (!result.next()) throw new SandboxDataNotFoundException("测试库中没有当前账号");
                        userId = result.getLong(1);
                    }
                }
                Map<String, JsonNode> storedByCompany = existingCompanyLinks(connection, userId);
                var clean = objectMapper.createArrayNode();
                for (JsonNode item : source) {
                    String company = item.path("company").asText("").trim();
                    String url = item.path("url").asText("").trim();
                    if (company.isEmpty() || company.length() > 120 || url.length() > 2048
                        || (!url.isEmpty() && !(url.startsWith("https://") || url.startsWith("http://")))) {
                        throw new IllegalArgumentException("公司名称或官网地址无效");
                    }
                    JsonNode previous = storedByCompany.get(companyKey(company));
                    var normalized = clean.addObject().put("company", company).put("url", url);
                    String industryVersion = item.path("industryVersion").asText("").trim();
                    if (industryVersion.isEmpty() && previous != null) industryVersion = previous.path("industryVersion").asText("").trim();
                    String industry = item.path("industry").asText("").trim();
                    if (industry.isEmpty() && previous != null) industry = previous.path("industry").asText("").trim();
                    String industryError = item.path("industryError").asText("").trim();
                    if (industryError.isEmpty() && previous != null) industryError = previous.path("industryError").asText("").trim();
                    if (!industry.isEmpty() && CompanyIndustryCatalog.ALLOWED.contains(industry)
                        && CompanyIndustryCatalog.VERSION.equals(industryVersion)) {
                        normalized.put("industry", industry);
                    }
                    String industryStatus = item.path("industryStatus").asText("").trim();
                    if (industryStatus.isEmpty() && previous != null) industryStatus = previous.path("industryStatus").asText("").trim();
                    if (!CompanyIndustryCatalog.VERSION.equals(industryVersion) || !CompanyIndustryCatalog.ALLOWED.contains(industry)) {
                        industryStatus = "pending";
                        industryVersion = CompanyIndustryCatalog.VERSION;
                        industryError = "";
                    }
                    if (!industryStatus.isEmpty()) normalized.put("industryStatus", industryStatus);
                    if (!industryVersion.isEmpty()) normalized.put("industryVersion", industryVersion);
                    if (!industryError.isEmpty()) normalized.put("industryError", industryError.substring(0, Math.min(300, industryError.length())));
                    JsonNode salaryEstimates = item.path("salaryEstimates");
                    if (previous != null && previous.path("salaryEstimates").isArray() && !previous.path("salaryEstimates").isEmpty()) salaryEstimates = previous.path("salaryEstimates");
                    if (salaryEstimates.isArray() && !salaryEstimates.isEmpty()) normalized.set("salaryEstimates", salaryEstimates.deepCopy());
                }
                String updatedAt;
                try (PreparedStatement save = connection.prepareStatement(
                    "INSERT INTO company_links(user_id,items) VALUES(?,?::jsonb) ON CONFLICT(user_id) DO UPDATE SET items=EXCLUDED.items,updated_at=NOW() RETURNING updated_at"
                )) {
                    save.setLong(1, userId); save.setString(2, objectMapper.writeValueAsString(clean));
                    try (ResultSet saved = save.executeQuery()) {
                        saved.next();
                        updatedAt = instant(saved, "updated_at");
                    }
                }
                connection.commit();
                return new CompanyLinks(clean, updatedAt);
            } catch (Exception exception) { connection.rollback(); throw exception; }
        }
    }

    public CompanyLinks ensureCompanyEntries(String email, List<String> companyNames) throws Exception {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                long userId;
                try (PreparedStatement user = connection.prepareStatement("SELECT id FROM users WHERE lower(email)=? AND disabled_at IS NULL FOR UPDATE")) {
                    user.setString(1, normalizeEmail(email));
                    try (ResultSet result = user.executeQuery()) {
                        if (!result.next()) throw new SandboxDataNotFoundException("测试库中没有当前账号");
                        userId = result.getLong(1);
                    }
                }
                JsonNode stored = currentCompanyLinks(connection, userId);
                var items = stored.isArray() ? (tools.jackson.databind.node.ArrayNode) stored.deepCopy() : objectMapper.createArrayNode();
                Map<String, tools.jackson.databind.node.ObjectNode> byCompany = new LinkedHashMap<>();
                for (JsonNode item : items) {
                    String company = item.path("company").asText("").trim();
                    if (!company.isEmpty()) byCompany.putIfAbsent(companyKey(company), (tools.jackson.databind.node.ObjectNode) item);
                }
                boolean changed = false;
                for (String rawName : companyNames == null ? List.<String>of() : companyNames) {
                    String company = rawName == null ? "" : rawName.trim();
                    if (company.isEmpty() || company.length() > 120) continue;
                    String key = companyKey(company);
                    var entry = byCompany.get(key);
                    if (entry == null) {
                        entry = items.addObject().put("company", company).put("url", "").put("industryStatus", "pending")
                            .put("industryVersion", CompanyIndustryCatalog.VERSION);
                        byCompany.put(key, entry);
                        changed = true;
                    } else if (!CompanyIndustryCatalog.VERSION.equals(entry.path("industryVersion").asText(""))) {
                        entry.remove("industry");
                        entry.remove("industryError");
                        entry.put("industryStatus", "pending").put("industryVersion", CompanyIndustryCatalog.VERSION);
                        changed = true;
                    } else if (entry.path("industry").asText("").isBlank()
                        && !Set.of("pending", "failed").contains(entry.path("industryStatus").asText(""))) {
                        entry.put("industryStatus", "pending").remove("industryError");
                        changed = true;
                    }
                }
                String updatedAt;
                if (changed) {
                    try (PreparedStatement save = connection.prepareStatement(
                        "INSERT INTO company_links(user_id,items) VALUES(?,?::jsonb) ON CONFLICT(user_id) DO UPDATE SET items=EXCLUDED.items,updated_at=NOW() RETURNING updated_at"
                    )) {
                        save.setLong(1, userId); save.setString(2, objectMapper.writeValueAsString(items));
                        try (ResultSet saved = save.executeQuery()) { saved.next(); updatedAt = instant(saved, "updated_at"); }
                    }
                } else {
                    try (PreparedStatement read = connection.prepareStatement("SELECT updated_at FROM company_links WHERE user_id=?")) {
                        read.setLong(1, userId);
                        try (ResultSet result = read.executeQuery()) { updatedAt = result.next() ? instant(result, "updated_at") : ""; }
                    }
                }
                connection.commit();
                return new CompanyLinks(items, updatedAt);
            } catch (Exception exception) { connection.rollback(); throw exception; }
        }
    }

    public CompanyLinks saveIndustryResults(String email, Map<String, String> industries, String error) throws Exception {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                long userId;
                try (PreparedStatement user = connection.prepareStatement("SELECT id FROM users WHERE lower(email)=? AND disabled_at IS NULL FOR UPDATE")) {
                    user.setString(1, normalizeEmail(email));
                    try (ResultSet result = user.executeQuery()) {
                        if (!result.next()) throw new SandboxDataNotFoundException("测试库中没有当前账号");
                        userId = result.getLong(1);
                    }
                }
                var items = (tools.jackson.databind.node.ArrayNode) currentCompanyLinks(connection, userId).deepCopy();
                Map<String, String> normalizedResults = new LinkedHashMap<>();
                if (industries != null) industries.forEach((name, industry) -> normalizedResults.put(companyKey(name), industry));
                for (JsonNode node : items) {
                    var entry = (tools.jackson.databind.node.ObjectNode) node;
                    String key = companyKey(entry.path("company").asText(""));
                    String industry = normalizedResults.get(key);
                    if (industry != null && CompanyIndustryCatalog.ALLOWED.contains(industry)) {
                        entry.put("industry", industry).put("industryStatus", "classified")
                            .put("industryVersion", CompanyIndustryCatalog.VERSION).remove("industryError");
                    } else if (error != null && !error.isBlank() && "pending".equals(entry.path("industryStatus").asText(""))) {
                        entry.put("industryStatus", "failed").put("industryVersion", CompanyIndustryCatalog.VERSION)
                            .put("industryError", error.substring(0, Math.min(300, error.length())));
                    }
                }
                try (PreparedStatement save = connection.prepareStatement(
                    "INSERT INTO company_links(user_id,items) VALUES(?,?::jsonb) ON CONFLICT(user_id) DO UPDATE SET items=EXCLUDED.items,updated_at=NOW() RETURNING updated_at"
                )) {
                    save.setLong(1, userId); save.setString(2, objectMapper.writeValueAsString(items));
                    String updatedAt;
                    try (ResultSet saved = save.executeQuery()) {
                        if (!saved.next()) throw new IllegalStateException("公司行业标签保存失败");
                        updatedAt = instant(saved, "updated_at");
                    }
                    connection.commit();
                    return new CompanyLinks(items, updatedAt);
                }
            } catch (Exception exception) { connection.rollback(); throw exception; }
        }
    }

    public CompanyLinks ensureSalaryEntries(String email, List<com.jobtracker.careerflow.database.AiService.CompanyPosition> positions) throws Exception {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                long userId;
                try (PreparedStatement user = connection.prepareStatement("SELECT id FROM users WHERE lower(email)=? AND disabled_at IS NULL FOR UPDATE")) {
                    user.setString(1, normalizeEmail(email));
                    try (ResultSet result = user.executeQuery()) {
                        if (!result.next()) throw new SandboxDataNotFoundException("测试库中没有当前账号");
                        userId = result.getLong(1);
                    }
                }
                var items = (tools.jackson.databind.node.ArrayNode) currentCompanyLinks(connection, userId).deepCopy();
                Map<String, tools.jackson.databind.node.ObjectNode> byCompany = new LinkedHashMap<>();
                for (JsonNode node : items) if (node instanceof tools.jackson.databind.node.ObjectNode entry)
                    byCompany.putIfAbsent(companyKey(entry.path("company").asText("")), entry);
                boolean changed = false;
                for (var position : positions == null ? List.<com.jobtracker.careerflow.database.AiService.CompanyPosition>of() : positions) {
                    String company = position.company() == null ? "" : position.company().trim();
                    String title = position.position() == null ? "" : position.position().trim();
                    if (company.isEmpty() || title.isEmpty()) continue;
                    var entry = byCompany.get(companyKey(company));
                    if (entry == null || !"classified".equals(entry.path("industryStatus").asText(""))) continue;
                    var estimates = entry.path("salaryEstimates").isArray()
                        ? (tools.jackson.databind.node.ArrayNode) entry.path("salaryEstimates") : entry.putArray("salaryEstimates");
                    tools.jackson.databind.node.ObjectNode existing = null;
                    for (JsonNode estimate : estimates) if (positionKey(estimate.path("position").asText("")).equals(positionKey(title))) {
                        if (estimate instanceof tools.jackson.databind.node.ObjectNode object) existing = object;
                        break;
                    }
                    if (existing == null) {
                        estimates.addObject().put("position", title).put("status", "pending").put("version", SALARY_ESTIMATE_VERSION);
                        changed = true;
                    } else if (!SALARY_ESTIMATE_VERSION.equals(existing.path("version").asText(""))) {
                        existing.put("position", title).put("version", SALARY_ESTIMATE_VERSION);
                        if (!"classified".equals(existing.path("status").asText("")) || existing.path("range").asText("").isBlank()) {
                            existing.put("status", "pending");
                            existing.remove("range");
                            existing.remove("error");
                            existing.remove("retryAfter");
                        }
                        changed = true;
                    } else if ("failed".equals(existing.path("status").asText(""))) {
                        Instant retryAfter = null;
                        try { retryAfter = Instant.parse(existing.path("retryAfter").asText("")); }
                        catch (RuntimeException ignored) { }
                        if (retryAfter == null || !retryAfter.isAfter(Instant.now())) {
                            existing.put("position", title).put("status", "pending");
                            existing.remove("error");
                            existing.remove("retryAfter");
                            changed = true;
                        }
                    }
                }
                String updatedAt = writeCompanyLinks(connection, userId, items, changed);
                connection.commit();
                return new CompanyLinks(items, updatedAt);
            } catch (Exception exception) { connection.rollback(); throw exception; }
        }
    }

    public CompanyLinks saveSalaryResults(String email, List<AiService.CompanyPosition> batch, Map<String, String> salaryRanges, String error) throws Exception {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                long userId;
                try (PreparedStatement user = connection.prepareStatement("SELECT id FROM users WHERE lower(email)=? AND disabled_at IS NULL FOR UPDATE")) {
                    user.setString(1, normalizeEmail(email));
                    try (ResultSet result = user.executeQuery()) {
                        if (!result.next()) throw new SandboxDataNotFoundException("测试库中没有当前账号");
                        userId = result.getLong(1);
                    }
                }
                var items = (tools.jackson.databind.node.ArrayNode) currentCompanyLinks(connection, userId).deepCopy();
                Map<String, String> results = new LinkedHashMap<>();
                if (salaryRanges != null) salaryRanges.forEach((key, value) -> results.put(key, value));
                Set<String> targetKeys = new java.util.HashSet<>();
                if (batch != null) for (AiService.CompanyPosition position : batch)
                    targetKeys.add(salaryKey(position.company(), position.position()));
                for (JsonNode node : items) if (node instanceof tools.jackson.databind.node.ObjectNode company && company.path("salaryEstimates").isArray()) {
                    for (JsonNode estimateNode : company.path("salaryEstimates")) if (estimateNode instanceof tools.jackson.databind.node.ObjectNode estimate
                        && "pending".equals(estimate.path("status").asText(""))) {
                        String key = salaryKey(company.path("company").asText(""), estimate.path("position").asText(""));
                        if (!targetKeys.contains(key)) continue;
                        String range = results.get(key);
                        if (range != null) estimate.put("range", range).put("status", "classified").put("version", SALARY_ESTIMATE_VERSION).remove("error");
                        else {
                            String failure = error == null || error.isBlank() ? "模型未能为该岗位返回有效薪资范围" : error.substring(0, Math.min(300, error.length()));
                            estimate.put("status", "failed").put("version", SALARY_ESTIMATE_VERSION)
                                .put("error", failure).put("retryAfter", Instant.now().plus(SALARY_RETRY_DELAY).toString());
                        }
                    }
                }
                String updatedAt = writeCompanyLinks(connection, userId, items, true);
                connection.commit();
                return new CompanyLinks(items, updatedAt);
            } catch (Exception exception) { connection.rollback(); throw exception; }
        }
    }

    private String writeCompanyLinks(Connection connection, long userId, JsonNode items, boolean changed) throws Exception {
        if (changed) {
            try (PreparedStatement save = connection.prepareStatement("INSERT INTO company_links(user_id,items) VALUES(?,?::jsonb) ON CONFLICT(user_id) DO UPDATE SET items=EXCLUDED.items,updated_at=NOW() RETURNING updated_at")) {
                save.setLong(1, userId); save.setString(2, objectMapper.writeValueAsString(items));
                try (ResultSet saved = save.executeQuery()) { saved.next(); return instant(saved, "updated_at"); }
            }
        }
        try (PreparedStatement read = connection.prepareStatement("SELECT updated_at FROM company_links WHERE user_id=?")) {
            read.setLong(1, userId);
            try (ResultSet result = read.executeQuery()) { return result.next() ? instant(result, "updated_at") : ""; }
        }
    }

    private String positionKey(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String salaryKey(String company, String position) { return companyKey(company) + "\n" + positionKey(position); }

    private Map<String, JsonNode> existingCompanyLinks(Connection connection, long userId) throws Exception {
        JsonNode items = currentCompanyLinks(connection, userId);
        Map<String, JsonNode> result = new LinkedHashMap<>();
        if (items.isArray()) for (JsonNode item : items) {
            String company = item.path("company").asText("").trim();
            if (!company.isEmpty()) result.putIfAbsent(companyKey(company), item);
        }
        return result;
    }

    private JsonNode currentCompanyLinks(Connection connection, long userId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT items::text FROM company_links WHERE user_id=? FOR UPDATE")) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? objectMapper.readTree(result.getString(1)) : objectMapper.createArrayNode(); }
        }
    }

    private static String companyKey(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\s·•・()（）\\[\\]【】._-]+", "")
            .replaceAll("(股份)?有限公司$|有限责任公司$|集团$|公司$", "");
    }
    public BackupPage backups(String email) throws Exception {
        try (Connection connection = openConnection()) {
            UserDocument current = currentUserDocument(connection, email, false);
            List<BackupItem> items = new ArrayList<>();
            String sql = "SELECT id,reason,created_at,octet_length(data::text) AS size,"
                + "CASE WHEN jsonb_typeof(data->'applications')='array' THEN jsonb_array_length(data->'applications') ELSE 0 END AS application_count,"
                + "CASE WHEN jsonb_typeof(data->'events')='array' THEN jsonb_array_length(data->'events') ELSE 0 END AS event_count "
                + "FROM data_backups WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT 30";
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

    public ImportResult importDocument(String email, String documentJson, String reason) throws Exception {
        BackupSummary summary = validator.validate(documentJson);
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                UserDocument current = currentUserDocument(connection, email, true);
                insertBackup(connection, current.userId(), current.json(), reason);
                String updatedAt = replaceDocument(connection, current.userId(), documentJson);
                pruneBackups(connection, current.userId());
                connection.commit();
                return new ImportResult(summary.applicationCount(), summary.eventCount(), updatedAt);
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public ImportResult clearDocument(String email) throws Exception {
        return importDocument(email, "{\"applications\":[],\"events\":[]}", "poc-clear-data");
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
        LegacyDatabaseUrl config = LegacyDatabaseUrl.parse(com.jobtracker.careerflow.config.AppEnvironment.databaseUrl(environment));
        Properties properties = new Properties();
        if (config.username() != null) properties.setProperty("user", config.username());
        if (config.password() != null) properties.setProperty("password", config.password());
        properties.setProperty("ApplicationName", "careerflow-backup-sandbox");
        return PooledConnections.open(config, properties);
    }

    private String instant(ResultSet result, String column) throws Exception {
        return DatabaseTime.instant(result, column);
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

    public record CompanyLinks(JsonNode items, String updatedAt) {}
    public record BackupPage(List<BackupItem> items, String currentUpdatedAt) {}
    public record RestoreResult(long backupId, int applicationCount, int eventCount, String currentUpdatedAt) {}
    public record ImportResult(int applicationCount, int eventCount, String currentUpdatedAt) {}

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
