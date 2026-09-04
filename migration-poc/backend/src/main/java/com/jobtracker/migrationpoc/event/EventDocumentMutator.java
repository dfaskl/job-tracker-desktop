package com.jobtracker.migrationpoc.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class EventDocumentMutator {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Set<String> TYPES = Set.of("测评", "笔试", "面试", "Offer", "其他");
    private static final Set<String> PROGRESS_TYPES = Set.of("测评", "笔试", "面试", "Offer");
    private static final Set<String> TERMINAL_STATUSES = Set.of("已通过", "未通过", "已放弃", "已结束");

    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public EventDocumentMutator(ObjectMapper objectMapper) {
        this(objectMapper, Clock.systemUTC());
    }

    EventDocumentMutator(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public Mutation create(String json, EventInput input) throws Exception {
        BusinessDocument document = document(json);
        EventInput clean = validate(input);
        ObjectNode application = application(document.applications(), clean.applicationId());
        String now = now();
        ObjectNode event = objectMapper.createObjectNode();
        applyFields(event, clean, application);
        event.put("id", id("evt"));
        event.put("completed", false);
        event.put("missed", false);
        event.put("createdAt", now);
        event.put("updatedAt", now);
        document.events().add(event);
        applyCreatedProgress(application, event, now);
        return mutation(document.root(), event, document.events().size());
    }

    public Mutation update(String json, String id, EventInput input, String expectedUpdatedAt) throws Exception {
        BusinessDocument document = document(json);
        int index = find(document.events(), id);
        if (index < 0) throw new NotFoundException("日程不存在");
        ObjectNode previous = requireObject(document.events().get(index), "日程不是 JSON 对象");
        assertVersion(previous, expectedUpdatedAt);
        EventInput clean = validate(input);
        if (!clean.applicationId().equals(text(previous, "applicationId"))) {
            throw new ValidationException("编辑日程时不能更换关联岗位");
        }
        ObjectNode application = application(document.applications(), clean.applicationId());
        ObjectNode updated = previous.deepCopy();
        applyFields(updated, clean, application);
        if (clean.endsAt().isEmpty()) updated.remove("completedAt");
        updated.put("updatedAt", now());
        document.events().set(index, updated);
        return mutation(document.root(), updated, document.events().size());
    }

    public Mutation resolve(
        String json,
        String id,
        Resolution resolution,
        String expectedUpdatedAt
    ) throws Exception {
        BusinessDocument document = document(json);
        int index = find(document.events(), id);
        if (index < 0) throw new NotFoundException("日程不存在");
        ObjectNode previous = requireObject(document.events().get(index), "日程不是 JSON 对象");
        assertVersion(previous, expectedUpdatedAt);
        ObjectNode updated = previous.deepCopy();
        String now = now();
        switch (resolution) {
            case COMPLETE -> {
                updated.put("completed", true);
                updated.put("missed", false);
                if (hasRange(updated)) updated.put("completedAt", now);
                else updated.remove("completedAt");
            }
            case MISS -> {
                updated.put("completed", true);
                updated.put("missed", true);
                if (hasRange(updated)) updated.put("completedAt", now);
                else updated.remove("completedAt");
            }
            case RESTORE -> {
                updated.put("completed", false);
                updated.put("missed", false);
                updated.remove("completedAt");
            }
        }
        updated.put("updatedAt", now);
        document.events().set(index, updated);
        return mutation(document.root(), updated, document.events().size());
    }

    public Mutation delete(String json, String id, String expectedUpdatedAt) throws Exception {
        BusinessDocument document = document(json);
        int index = find(document.events(), id);
        if (index < 0) throw new NotFoundException("日程不存在");
        ObjectNode removed = requireObject(document.events().get(index), "日程不是 JSON 对象");
        assertVersion(removed, expectedUpdatedAt);
        int applicationIndex = find(document.applications(), text(removed, "applicationId"));
        document.events().remove(index);
        if (applicationIndex >= 0) {
            ObjectNode application = requireObject(
                document.applications().get(applicationIndex), "投递记录不是 JSON 对象"
            );
            removeEventHistory(application, removed);
            rollbackProgress(application, removed, document.events());
            application.put("updatedAt", now());
        }
        return mutation(document.root(), removed, document.events().size());
    }

    public EventPage page(String json, int maximum) throws Exception {
        BusinessDocument document = document(json);
        List<EventView> events = new ArrayList<>();
        for (JsonNode item : document.events()) events.add(view(requireObject(item, "日程不是 JSON 对象")));
        events.sort(Comparator.comparing(EventView::recordAt).thenComparing(EventView::id));
        int total = events.size();
        List<EventView> visible = total > maximum ? List.copyOf(events.subList(0, maximum)) : List.copyOf(events);
        List<ApplicationOption> applications = new ArrayList<>();
        for (JsonNode item : document.applications()) {
            ObjectNode application = requireObject(item, "投递记录不是 JSON 对象");
            applications.add(new ApplicationOption(
                text(application, "id"), text(application, "company"), text(application, "position"),
                text(application, "appliedDate").isEmpty() ? text(application, "createdAt") : text(application, "appliedDate")
            ));
        }
        return new EventPage(visible, List.copyOf(applications), total, total > maximum);
    }

    private BusinessDocument document(String json) throws Exception {
        JsonNode parsed = objectMapper.readTree(json == null ? "{}" : json);
        if (!(parsed instanceof ObjectNode root)) throw new ValidationException("业务数据不是 JSON 对象");
        if (!(root.path("applications") instanceof ArrayNode applications)) {
            throw new ValidationException("applications 不是数组");
        }
        if (!(root.path("events") instanceof ArrayNode events)) throw new ValidationException("events 不是数组");
        return new BusinessDocument(root, applications, events);
    }

    private EventInput validate(EventInput input) {
        if (input == null) throw new ValidationException("请求内容不能为空");
        String applicationId = required(input.applicationId(), "关联岗位", 160);
        String type = required(input.type(), "日程类型", 40);
        if (!TYPES.contains(type)) throw new ValidationException("日程类型不受支持");
        String title = required(input.title(), "安排名称", 200);
        String startsAt = time(required(input.startsAt(), "开始时间", 16), "开始时间");
        String endsAt = optional(input.endsAt(), 16);
        if (!endsAt.isEmpty()) {
            endsAt = time(endsAt, "结束时间");
            if (!parse(endsAt).isAfter(parse(startsAt))) {
                throw new ValidationException("结束时间必须晚于开始时间");
            }
        }
        return new EventInput(
            applicationId, type, title, startsAt, endsAt,
            optional(input.location(), 1_000), optional(input.notes(), 4_000)
        );
    }

    private void applyFields(ObjectNode event, EventInput input, ObjectNode application) {
        event.put("applicationId", input.applicationId());
        event.put("type", input.type());
        event.put("title", input.title());
        event.put("startsAt", input.startsAt());
        if (input.endsAt().isEmpty()) event.remove("endsAt");
        else event.put("endsAt", input.endsAt());
        event.put("location", input.location());
        event.put("notes", input.notes());
        event.put("company", text(application, "company"));
        event.put("position", text(application, "position"));
    }

    private void applyCreatedProgress(ObjectNode application, ObjectNode event, String now) {
        String type = text(event, "type");
        if (!PROGRESS_TYPES.contains(type)) return;
        application.put("stage", type);
        application.put("status", "Offer".equals(type) ? "已通过" : "等待结果");
        ArrayNode timeline = application.path("timeline") instanceof ArrayNode array
            ? array
            : objectMapper.createArrayNode();
        ObjectNode entry = objectMapper.createObjectNode();
        entry.put("id", id("tl"));
        entry.put("eventId", text(event, "id"));
        entry.put("at", now);
        entry.put("title", "新增" + type + "安排：" + text(event, "title"));
        timeline.insert(0, entry);
        application.set("timeline", timeline);
        application.put("updatedAt", now);
    }

    private void removeEventHistory(ObjectNode application, ObjectNode event) {
        if (!(application.path("timeline") instanceof ArrayNode timeline)) return;
        String eventId = text(event, "id");
        String createdAt = text(event, "createdAt");
        String type = text(event, "type");
        String title = text(event, "title");
        Set<String> expectedTitles = Set.of(
            "新增" + type + "安排：" + (title.isEmpty() ? type : title),
            "邮件识别追加" + type + "安排：" + (title.isEmpty() ? type : title)
        );
        for (int index = timeline.size() - 1; index >= 0; index--) {
            JsonNode item = timeline.get(index);
            boolean linked = eventId.equals(text(item, "eventId"));
            boolean legacyMatch = createdAt.equals(text(item, "at")) && expectedTitles.contains(text(item, "title"));
            if (linked || legacyMatch) timeline.remove(index);
        }
    }

    private void rollbackProgress(ObjectNode application, ObjectNode removed, ArrayNode remainingEvents) {
        String stage = text(application, "stage");
        String status = text(application, "status");
        boolean terminal = "已结束".equals(stage) || TERMINAL_STATUSES.contains(status);
        if (terminal || !stage.equals(text(removed, "type"))) return;
        ObjectNode latest = null;
        for (JsonNode candidateNode : remainingEvents) {
            if (!(candidateNode instanceof ObjectNode candidate)) continue;
            if (!text(application, "id").equals(text(candidate, "applicationId"))) continue;
            if (!PROGRESS_TYPES.contains(text(candidate, "type"))) continue;
            if (latest == null || recordAt(candidate).compareTo(recordAt(latest)) > 0) latest = candidate;
        }
        String nextStage = latest == null ? "已投递" : text(latest, "type");
        application.put("stage", nextStage);
        application.put("status", "Offer".equals(nextStage) ? "已通过" : "等待结果");
    }

    private ObjectNode application(ArrayNode applications, String id) {
        int index = find(applications, id);
        if (index < 0) throw new ValidationException("关联岗位不存在");
        return requireObject(applications.get(index), "投递记录不是 JSON 对象");
    }

    private Mutation mutation(ObjectNode root, ObjectNode event, int total) throws Exception {
        return new Mutation(objectMapper.writeValueAsString(root), view(event), total);
    }

    private EventView view(ObjectNode event) {
        return new EventView(
            text(event, "id"), text(event, "applicationId"), text(event, "type"), text(event, "title"),
            text(event, "startsAt"), text(event, "endsAt"), text(event, "location"), text(event, "notes"),
            text(event, "company"), text(event, "position"), event.path("completed").asBoolean(false),
            event.path("missed").asBoolean(false), text(event, "completedAt"), text(event, "createdAt"),
            version(event), recordAt(event)
        );
    }

    private void assertVersion(ObjectNode event, String expectedUpdatedAt) {
        if (expectedUpdatedAt == null || expectedUpdatedAt.isBlank()) {
            throw new ValidationException("缺少日程版本，请刷新后重试");
        }
        if (!expectedUpdatedAt.equals(version(event))) throw new ConflictException("日程已更新，请刷新后重试");
    }

    private String version(ObjectNode event) {
        String updatedAt = text(event, "updatedAt");
        return updatedAt.isEmpty() ? text(event, "createdAt") : updatedAt;
    }

    private String recordAt(ObjectNode event) {
        if (hasRange(event) && event.path("completed").asBoolean(false)) {
            String completedAt = text(event, "completedAt");
            if (!completedAt.isEmpty()) return completedAt;
            String endsAt = text(event, "endsAt");
            if (!endsAt.isEmpty()) return endsAt;
        }
        return text(event, "startsAt");
    }

    private boolean hasRange(ObjectNode event) {
        return !text(event, "endsAt").isEmpty();
    }

    private int find(ArrayNode values, String id) {
        if (id == null || id.isBlank()) return -1;
        for (int index = 0; index < values.size(); index++) {
            if (id.equals(text(values.get(index), "id"))) return index;
        }
        return -1;
    }

    private ObjectNode requireObject(JsonNode value, String message) {
        if (value instanceof ObjectNode object) return object;
        throw new ValidationException(message);
    }

    private String required(String value, String label, int maximum) {
        String clean = optional(value, maximum);
        if (clean.isEmpty()) throw new ValidationException(label + "不能为空");
        return clean;
    }

    private String optional(String value, int maximum) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() > maximum) throw new ValidationException("字段内容过长");
        return clean;
    }

    private String time(String value, String label) {
        try {
            parse(value);
            return value;
        } catch (DateTimeParseException exception) {
            throw new ValidationException(label + "格式必须为 YYYY-MM-DD HH:mm");
        }
    }

    private LocalDateTime parse(String value) {
        return LocalDateTime.parse(value, TIME_FORMAT);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isValueNode() ? value.asText("").trim() : "";
    }

    private String now() {
        return LocalDateTime.now(clock).format(TIME_FORMAT);
    }

    private String id(String prefix) {
        return prefix + "_" + clock.millis() + "_" + UUID.randomUUID().toString().substring(0, 5);
    }

    private record BusinessDocument(ObjectNode root, ArrayNode applications, ArrayNode events) {}

    public enum Resolution { COMPLETE, MISS, RESTORE }

    public record EventInput(
        String applicationId,
        String type,
        String title,
        String startsAt,
        String endsAt,
        String location,
        String notes
    ) {}

    public record EventView(
        String id,
        String applicationId,
        String type,
        String title,
        String startsAt,
        String endsAt,
        String location,
        String notes,
        String company,
        String position,
        boolean completed,
        boolean missed,
        String completedAt,
        String createdAt,
        String updatedAt,
        String recordAt
    ) {}

    public record ApplicationOption(String id, String company, String position, String appliedDate) {}
    public record EventPage(List<EventView> events, List<ApplicationOption> applications, int total, boolean truncated) {}
    public record Mutation(String documentJson, EventView event, int total) {}

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
