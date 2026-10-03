package com.jobtracker.careerflow.database;

import com.jobtracker.careerflow.database.AiService.AiResponseException;
import com.jobtracker.careerflow.database.AiService.AiValidationException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@Component
public class InterviewWorkbenchService {
    private final Environment environment;
    private final ApplicationService applications;
    private final ObjectMapper mapper;
    private final AiService ai;

    public InterviewWorkbenchService(Environment environment, ApplicationService applications, ObjectMapper mapper, AiService ai) {
        this.environment = environment;
        this.applications = applications;
        this.mapper = mapper;
        this.ai = ai;
    }

    public ObjectNode state(String email) throws Exception {
        try (Connection connection = open()) {
            Document document = read(connection, email, false);
            ObjectNode result = workbench(document.root()).deepCopy();
            ArrayNode allReviews = reviews(document.root());
            String currentKey = sourceKey(allReviews);
            result.put("sourceKey", currentKey);
            JsonNode classification = result.path("classification");
            JsonNode summaries = result.path("summaries");
            if (summaries instanceof ObjectNode saved) {
                for (JsonNode group : classification.path("categories")) {
                    String id = group.path("id").asText("");
                    if (!(saved.path(id) instanceof ObjectNode summary)) continue;
                    ArrayNode selected = summaryInput(allReviews, group);
                    String fingerprint = digest(selected.toString() + result.path("resume").toString());
                    summary.put("stale", !currentKey.equals(classification.path("sourceKey").asText(""))
                        || !fingerprint.equals(summary.path("sourceKey").asText("")));
                }
            }
            return result;
        }
    }

    public ObjectNode saveResume(String email, JsonNode input) throws Exception {
        ObjectNode resume = cleanResume(input);
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                Document document = read(connection, email, true);
                workbench(document.root()).set("resume", resume);
                write(connection, document);
                connection.commit();
            } catch (Exception exception) { connection.rollback(); throw exception; }
        }
        return state(email);
    }

    public ObjectNode classify(String email) throws Exception {
        Document snapshot;
        try (Connection connection = open()) { snapshot = read(connection, email, false); }
        ArrayNode reviews = reviews(snapshot.root());
        if (reviews.isEmpty()) throw new AiValidationException("请先在已完成的日程中记录面试回顾");
        ArrayNode positions = positions(reviews);
        if (positions.size() > 200) throw new AiValidationException("有回顾的岗位超过 200 个，暂不支持一次分类");
        String key = sourceKey(reviews);
        JsonNode result = ai.classifyInterviewPositions(email, positions);
        ObjectNode classification = cleanClassification(result, positions);
        classification.put("sourceKey", key);
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                Document current = read(connection, email, true);
                if (!key.equals(sourceKey(reviews(current.root())))) throw new WorkbenchConflictException("面试回顾已更新，请重新分类");
                ObjectNode workbench = workbench(current.root());
                workbench.set("classification", classification);
                workbench.set("summaries", mapper.createObjectNode());
                write(connection, current);
                connection.commit();
            } catch (Exception exception) { connection.rollback(); throw exception; }
        }
        return state(email);
    }

    public ObjectNode summarize(String email, String categoryId) throws Exception {
        Document snapshot;
        try (Connection connection = open()) { snapshot = read(connection, email, false); }
        ObjectNode workbench = workbench(snapshot.root());
        ArrayNode allReviews = reviews(snapshot.root());
        String key = sourceKey(allReviews);
        JsonNode classification = workbench.path("classification");
        if (!key.equals(classification.path("sourceKey").asText(""))) throw new WorkbenchConflictException("岗位分类已过期，请先重新分类");
        JsonNode category = category(classification, categoryId);
        if (category == null) throw new AiValidationException("请选择有效的岗位类别");
        ArrayNode selected = summaryInput(allReviews, category);
        int size = 0;
        for (JsonNode item : selected) size += item.toString().length();
        if (selected.isEmpty()) throw new AiValidationException("这个类别暂时没有面试回顾");
        if (size > 35_000) throw new AiValidationException("该类别的回顾内容过长，请缩减后再汇总");
        JsonNode resume = workbench.path("resume");
        String summaryKey = digest(selected.toString() + resume.toString());
        JsonNode result = ai.summarizeInterviewReviews(email, selected, resume);
        ObjectNode summary = cleanSummary(result);
        summary.put("sourceKey", summaryKey);
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                Document current = read(connection, email, true);
                ObjectNode currentWorkbench = workbench(current.root());
                if (!key.equals(sourceKey(reviews(current.root())))
                    || !currentWorkbench.path("resume").equals(resume)
                    || category(currentWorkbench.path("classification"), categoryId) == null) {
                    throw new WorkbenchConflictException("面试回顾或简历已更新，请重新汇总");
                }
                ObjectNode summaries = currentWorkbench.path("summaries") instanceof ObjectNode object ? object : currentWorkbench.putObject("summaries");
                summaries.set(categoryId, summary);
                write(connection, current);
                connection.commit();
            } catch (Exception exception) { connection.rollback(); throw exception; }
        }
        return state(email);
    }

    private ObjectNode cleanResume(JsonNode source) {
        if (source == null || !source.isObject()) throw new AiValidationException("简历内容格式无效");
        ObjectNode clean = mapper.createObjectNode();
        ArrayNode internships = clean.putArray("internships");
        ArrayNode projects = clean.putArray("projects");
        copyResumeItems(source.path("internships"), internships, new String[]{"company", "role", "description", "coreWork"});
        copyResumeItems(source.path("projects"), projects, new String[]{"name", "description", "coreWork"});
        if (clean.toString().length() > 20_000) throw new AiValidationException("简历内容不能超过 20000 个字符");
        return clean;
    }

    private void copyResumeItems(JsonNode source, ArrayNode target, String[] fields) {
        if (!source.isArray() || source.size() > 10) throw new AiValidationException("每类简历经历最多填写 10 项");
        for (JsonNode item : source) {
            if (!item.isObject()) throw new AiValidationException("简历条目格式无效");
            ObjectNode clean = target.addObject();
            for (String field : fields) {
                String value = item.path(field).asText("").trim();
                int maximum = field.equals("coreWork") ? 2_000 : field.equals("description") ? 1_000 : 120;
                if (value.length() > maximum) throw new AiValidationException("简历字段内容过长");
                clean.put(field, value);
            }
        }
    }

    private ObjectNode cleanClassification(JsonNode response, ArrayNode positions) {
        if (!response.path("categories").isArray()) throw new AiResponseException("AI 未返回岗位类别");
        Set<String> valid = new HashSet<>();
        for (JsonNode position : positions) valid.add(position.path("id").asText(""));
        Set<String> assigned = new HashSet<>();
        ObjectNode result = mapper.createObjectNode();
        ArrayNode categories = result.putArray("categories");
        for (JsonNode candidate : response.path("categories")) {
            if (categories.size() >= 8) break;
            String name = candidate.path("name").asText("").trim();
            if (name.isBlank() || name.length() > 30 || !candidate.path("applicationIds").isArray()) continue;
            String categoryId = "category-" + (categories.size() + 1);
            ObjectNode group = categories.addObject().put("id", categoryId).put("name", name);
            ArrayNode ids = group.putArray("applicationIds");
            for (JsonNode item : candidate.path("applicationIds")) {
                String id = item.asText("");
                if (valid.contains(id) && assigned.add(id)) ids.add(id);
            }
            if (ids.isEmpty()) categories.remove(categories.size() - 1);
        }
        if (assigned.size() < valid.size()) {
            ObjectNode other = categories.addObject().put("id", "category-other").put("name", "其他岗位");
            ArrayNode ids = other.putArray("applicationIds");
            for (JsonNode position : positions) {
                String id = position.path("id").asText("");
                if (assigned.add(id)) ids.add(id);
            }
        }
        if (categories.isEmpty()) throw new AiResponseException("AI 未返回有效岗位类别");
        return result;
    }

    private ObjectNode cleanSummary(JsonNode result) {
        if (!result.path("topics").isArray()) throw new AiResponseException("AI 未返回考点总结");
        ObjectNode clean = mapper.createObjectNode();
        ArrayNode topics = clean.putArray("topics");
        for (JsonNode topic : result.path("topics")) {
            if (topics.size() >= 20) break;
            String name = topic.path("name").asText("").trim();
            if (name.isEmpty()) continue;
            String kind = topic.path("kind").asText("other");
            if (!Set.of("project", "knowledge", "other").contains(kind)) kind = "other";
            ObjectNode item = topics.addObject().put("name", limit(name, 100))
                .put("count", Math.max(1, Math.min(1000, topic.path("count").asInt(1))))
                .put("kind", kind).put("summary", limit(topic.path("summary").asText(""), 500));
            ArrayNode questions = item.putArray("questions");
            if (topic.path("questions").isArray()) for (JsonNode question : topic.path("questions")) {
                if (questions.size() >= 5) break;
                String text = limit(question.asText("").trim(), 300);
                if (!text.isEmpty()) questions.add(text);
            }
        }
        if (topics.isEmpty()) throw new AiResponseException("AI 未返回有效考点");
        var sorted = new java.util.ArrayList<JsonNode>();
        for (JsonNode item : topics) sorted.add(item);
        sorted.sort((a, b) -> Integer.compare(b.path("count").asInt(), a.path("count").asInt()));
        topics.removeAll();
        for (JsonNode item : sorted) topics.add(item);
        return clean;
    }

    private ArrayNode reviews(ObjectNode root) {
        Map<String, JsonNode> applications = new HashMap<>();
        for (JsonNode app : root.path("applications")) applications.put(app.path("id").asText(""), app);
        ArrayNode reviews = mapper.createArrayNode();
        for (JsonNode event : root.path("events")) {
            String questions = event.path("interviewQuestions").asText("").trim();
            if (questions.isEmpty() || !event.path("completed").asBoolean(false)
                || event.path("missed").asBoolean(false) || event.path("abandoned").asBoolean(false)) continue;
            String appId = event.path("applicationId").asText("");
            JsonNode app = applications.get(appId);
            if (app == null) continue;
            reviews.addObject().put("eventId", event.path("id").asText(""))
                .put("applicationId", appId).put("company", limit(app.path("company").asText(""), 120))
                .put("position", limit(app.path("position").asText(""), 120))
                .put("title", limit(event.path("title").asText(event.path("type").asText("面试")), 120))
                .put("questions", limit(questions, 8_000));
        }
        return reviews;
    }

    private ArrayNode positions(ArrayNode reviews) {
        Map<String, JsonNode> distinct = new java.util.LinkedHashMap<>();
        for (JsonNode review : reviews) {
            String id = review.path("applicationId").asText("");
            if (!id.isBlank()) distinct.putIfAbsent(id, review);
        }
        ArrayNode result = mapper.createArrayNode();
        distinct.forEach((id, review) -> result.addObject().put("id", id)
            .put("company", review.path("company").asText(""))
            .put("position", review.path("position").asText("")));
        return result;
    }

    private ArrayNode summaryInput(ArrayNode allReviews, JsonNode category) {
        Set<String> ids = new HashSet<>();
        for (JsonNode id : category.path("applicationIds")) ids.add(id.asText(""));
        ArrayNode selected = mapper.createArrayNode();
        for (JsonNode review : allReviews) {
            if (!ids.contains(review.path("applicationId").asText(""))) continue;
            ObjectNode item = selected.addObject();
            for (String field : new String[]{"company", "position", "title", "questions"}) item.put(field, review.path(field).asText(""));
        }
        return selected;
    }

    private String sourceKey(ArrayNode reviews) throws Exception {
        var signatures = new java.util.ArrayList<String>();
        for (JsonNode review : reviews) signatures.add(review.path("eventId").asText("") + "|"
            + review.path("applicationId").asText("") + "|" + review.path("company").asText("") + "|"
            + review.path("position").asText(""));
        signatures.sort(String::compareTo);
        return signatures.isEmpty() ? "" : digest(String.join("\n", signatures));
    }

    private String digest(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private JsonNode category(JsonNode classification, String id) {
        if (!classification.path("categories").isArray()) return null;
        for (JsonNode item : classification.path("categories")) if (id != null && id.equals(item.path("id").asText(""))) return item;
        return null;
    }

    private ObjectNode workbench(ObjectNode root) {
        ObjectNode settings = root.path("settings") instanceof ObjectNode value ? value : root.putObject("settings");
        return settings.path("interviewWorkbench") instanceof ObjectNode value ? value : settings.putObject("interviewWorkbench");
    }

    private Document read(Connection connection, String email, boolean lock) throws Exception {
        String sql = "SELECT u.id,d.data::text FROM users u JOIN user_data d ON d.user_id=u.id WHERE lower(u.email)=? AND u.disabled_at IS NULL"
            + (lock ? " FOR UPDATE OF d" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new AiValidationException("找不到当前账号的业务数据");
                return new Document(result.getLong(1), (ObjectNode) mapper.readTree(result.getString(2)));
            }
        }
    }

    private void write(Connection connection, Document document) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE user_data SET data=?::jsonb,updated_at=NOW() WHERE user_id=?")) {
            statement.setString(1, mapper.writeValueAsString(document.root()));
            statement.setLong(2, document.userId());
            statement.executeUpdate();
        }
    }

    private Connection open() throws Exception {
        if (!applications.status().enabled()) throw new AiService.AiDisabledException("业务数据写入未开启");
        LegacyDatabaseUrl config = LegacyDatabaseUrl.parse(com.jobtracker.careerflow.config.AppEnvironment.databaseUrl(environment));
        Properties properties = new Properties();
        if (config.username() != null) properties.setProperty("user", config.username());
        if (config.password() != null) properties.setProperty("password", config.password());
        properties.setProperty("ApplicationName", "careerflow-interview-workbench");
        return PooledConnections.open(config, properties);
    }

    private String limit(String text, int maximum) { return text.length() <= maximum ? text : text.substring(0, maximum); }
    private record Document(long userId, ObjectNode root) {}
    public static class WorkbenchConflictException extends RuntimeException { public WorkbenchConflictException(String message) { super(message); } }
}
