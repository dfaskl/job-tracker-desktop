package com.jobtracker.migrationpoc.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;

@Component
public class ApplicationDocumentMutator {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Set<String> STAGES = Set.of("已投递", "测评", "笔试", "面试", "Offer", "已结束");
    private static final Set<String> STATUSES = Set.of("等待结果", "已通过", "未通过", "已放弃", "已结束");

    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public ApplicationDocumentMutator(ObjectMapper objectMapper) {
        this(objectMapper, Clock.systemUTC());
    }

    ApplicationDocumentMutator(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public Mutation create(String json, ApplicationInput input) throws Exception {
        BusinessDocument document = document(json);
        String now = now();
        String id = id("app");
        ObjectNode application = objectMapper.createObjectNode();
        applyFields(application, validate(input));
        application.put("id", id);
        application.put("createdAt", now);
        application.put("updatedAt", now);
        ArrayNode timeline = objectMapper.createArrayNode();
        timeline.add(timelineEntry("创建投递记录", now));
        application.set("timeline", timeline);
        document.applications().insert(0, application);
        return mutation(document.root(), application, document.applications().size());
    }

    public Mutation update(String json, String id, ApplicationInput input, String expectedUpdatedAt) throws Exception {
        BusinessDocument document = document(json);
        int index = find(document.applications(), id);
        if (index < 0) throw new NotFoundException("投递记录不存在");
        ObjectNode previous = requireObject(document.applications().get(index));
        assertVersion(previous, expectedUpdatedAt);

        String oldStage = text(previous, "stage");
        String oldStatus = text(previous, "status");
        ObjectNode updated = previous.deepCopy();
        ApplicationInput clean = validate(input);
        applyFields(updated, clean);
        String now = now();
        updated.put("updatedAt", now);
        if (!oldStage.equals(clean.stage()) || !oldStatus.equals(clean.status())) {
            ArrayNode timeline = updated.path("timeline").isArray()
                ? (ArrayNode) updated.path("timeline")
                : objectMapper.createArrayNode();
            timeline.insert(0, timelineEntry("更新为 " + clean.stage() + " · " + clean.status(), now));
            updated.set("timeline", timeline);
        }
        document.applications().set(index, updated);
        return mutation(document.root(), updated, document.applications().size());
    }

    public Mutation delete(String json, String id, String expectedUpdatedAt) throws Exception {
        BusinessDocument document = document(json);
        int index = find(document.applications(), id);
        if (index < 0) throw new NotFoundException("投递记录不存在");
        ObjectNode removed = requireObject(document.applications().get(index));
        assertVersion(removed, expectedUpdatedAt);
        document.applications().remove(index);
        for (int eventIndex = document.events().size() - 1; eventIndex >= 0; eventIndex--) {
            if (id.equals(text(document.events().get(eventIndex), "applicationId"))) {
                document.events().remove(eventIndex);
            }
        }
        return mutation(document.root(), removed, document.applications().size());
    }

    private BusinessDocument document(String json) throws Exception {
        JsonNode parsed = objectMapper.readTree(json == null ? "{}" : json);
        if (!(parsed instanceof ObjectNode root)) throw new ValidationException("业务数据不是 JSON 对象");
        if (!(root.path("applications") instanceof ArrayNode applications)) {
            throw new ValidationException("applications 不是数组");
        }
        if (!(root.path("events") instanceof ArrayNode events)) {
            throw new ValidationException("events 不是数组");
        }
        return new BusinessDocument(root, applications, events);
    }

    private ApplicationInput validate(ApplicationInput input) {
        if (input == null) throw new ValidationException("请求内容不能为空");
        String company = required(input.company(), "公司名称", 120);
        String position = required(input.position(), "岗位名称", 160);
        String city = optional(input.city(), 120);
        String channel = optional(input.channel(), 80);
        String appliedDate = optional(input.appliedDate(), 10);
        if (!appliedDate.isEmpty()) {
            try {
                LocalDate.parse(appliedDate);
            } catch (DateTimeParseException exception) {
                throw new ValidationException("投递日期格式必须为 YYYY-MM-DD");
            }
        }
        String stage = optional(input.stage(), 40);
        if (stage.isEmpty()) stage = "已投递";
        if (!STAGES.contains(stage)) throw new ValidationException("投递阶段不受支持");
        String status = optional(input.status(), 40);
        if (status.isEmpty()) status = "等待结果";
        if (!STATUSES.contains(status)) throw new ValidationException("投递状态不受支持");
        String notes = optional(input.notes(), 4_000);
        return new ApplicationInput(company, position, city, channel, appliedDate, stage, status, notes);
    }

    private void applyFields(ObjectNode application, ApplicationInput input) {
        application.put("company", input.company());
        application.put("position", input.position());
        application.put("city", input.city());
        application.put("channel", input.channel());
        application.put("appliedDate", input.appliedDate());
        application.put("stage", input.stage());
        application.put("status", input.status());
        application.put("notes", input.notes());
    }

    private ObjectNode timelineEntry(String title, String now) {
        ObjectNode entry = objectMapper.createObjectNode();
        entry.put("id", id("tl"));
        entry.put("at", now);
        entry.put("title", title);
        return entry;
    }

    private Mutation mutation(ObjectNode root, ObjectNode application, int total) throws Exception {
        return new Mutation(objectMapper.writeValueAsString(root), view(application), total);
    }

    private ApplicationView view(ObjectNode application) {
        return new ApplicationView(
            text(application, "id"),
            text(application, "company"),
            text(application, "position"),
            text(application, "city"),
            text(application, "channel"),
            text(application, "appliedDate"),
            text(application, "stage"),
            text(application, "status"),
            text(application, "notes"),
            text(application, "createdAt"),
            text(application, "updatedAt")
        );
    }

    private void assertVersion(ObjectNode application, String expectedUpdatedAt) {
        if (expectedUpdatedAt == null || expectedUpdatedAt.isBlank()) {
            throw new ValidationException("缺少记录版本，请刷新后重试");
        }
        if (!expectedUpdatedAt.equals(text(application, "updatedAt"))) {
            throw new ConflictException("记录已被其他操作更新，请刷新后重试");
        }
    }

    private int find(ArrayNode applications, String id) {
        if (id == null || id.isBlank()) return -1;
        for (int index = 0; index < applications.size(); index++) {
            if (id.equals(text(applications.get(index), "id"))) return index;
        }
        return -1;
    }

    private ObjectNode requireObject(JsonNode value) {
        if (value instanceof ObjectNode object) return object;
        throw new ValidationException("投递记录不是 JSON 对象");
    }

    private String required(String value, String label, int maxLength) {
        String clean = optional(value, maxLength);
        if (clean.isEmpty()) throw new ValidationException(label + "不能为空");
        return clean;
    }

    private String optional(String value, int maxLength) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() > maxLength) throw new ValidationException("字段内容过长");
        return clean;
    }

    private String text(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    private String now() {
        return LocalDateTime.now(clock).format(TIME_FORMAT);
    }

    private String id(String prefix) {
        return prefix + "_" + clock.millis() + "_" + UUID.randomUUID().toString().substring(0, 5);
    }

    private record BusinessDocument(ObjectNode root, ArrayNode applications, ArrayNode events) {}

    public record ApplicationInput(
        String company,
        String position,
        String city,
        String channel,
        String appliedDate,
        String stage,
        String status,
        String notes
    ) {}

    public record ApplicationView(
        String id,
        String company,
        String position,
        String city,
        String channel,
        String appliedDate,
        String stage,
        String status,
        String notes,
        String createdAt,
        String updatedAt
    ) {}

    public record Mutation(String documentJson, ApplicationView application, int total) {}

    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) { super(message); }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) { super(message); }
    }
}
