package com.jobtracker.careerflow.mcp;

import com.jobtracker.careerflow.application.ApplicationDocumentMutator.ApplicationInput;
import com.jobtracker.careerflow.application.ApplicationDocumentMutator.ApplicationView;
import com.jobtracker.careerflow.database.ApplicationService;
import com.jobtracker.careerflow.database.CompanyIndustryService;
import com.jobtracker.careerflow.database.EventService;
import com.jobtracker.careerflow.event.EventDocumentMutator.EventInput;
import com.jobtracker.careerflow.event.EventDocumentMutator.EventView;
import com.jobtracker.careerflow.event.EventDocumentMutator.Resolution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;

@Component
public class McpTools {
    private static final Logger LOGGER = LoggerFactory.getLogger(McpTools.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT);
    private final ApplicationService applications;
    private final EventService events;
    private final CompanyIndustryService companies;
    private final McpTokenStore tokens;
    private final ObjectMapper mapper;
    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public McpTools(ApplicationService applications, EventService events, CompanyIndustryService companies, McpTokenStore tokens, ObjectMapper mapper) {
        this.applications = applications; this.events = events; this.companies = companies; this.tokens = tokens; this.mapper = mapper;
        register("search_applications", "查询投递", "applications:read", true,
            "搜索当前账户的投递。返回记录 ID 和 updatedAt；修改前先读取。分页返回，不包含其他账户数据。",
            fields("query", text("公司、岗位、城市或备注关键词", 120), "stage", text("当前阶段，可留空", 20), "limit", integer(1, 50), "offset", integer(0, 10000)));
        register("get_application", "读取投递详情", "applications:read", true, "读取当前账户的一条投递及其岗位备注。",
            fields("id", text("投递 ID", 120)), "id");
        register("create_application", "新增投递", "applications:write", false,
            "新增投递。notes 仅为岗位备注。generateCompanyInsights=true 会触发站内 AI 分类与薪资估算，默认不立即调用站内 AI。",
            applicationFields(false), "company", "position");
        register("update_application", "修改投递", "applications:write", false,
            "只修改明确提供的字段。notes 是岗位备注，不能用于保存日程备注。需提供先前读取的 updatedAt 作为 expectedUpdatedAt；冲突时重新读取。",
            applicationFields(true), "id", "expectedUpdatedAt");
        register("list_events", "查询日程", "events:read", true,
            "查询当前账户日程，按剩余时间排序：时间点按 startsAt，时间段按 endsAt。from/to 过滤同一排序时间，Asia/Shanghai。列表不返回长备注，详情请用 get_event。",
            fields("query", text("公司、岗位或日程名称关键词", 120), "applicationId", text("关联投递 ID", 120),
                "status", choice("all", "pending", "completed", "missed", "abandoned"), "from", text("下界 YYYY-MM-DD HH:mm", 16),
                "to", text("上界 YYYY-MM-DD HH:mm", 16), "limit", integer(1, 50), "offset", integer(0, 10000)));
        register("get_event", "读取日程详情", "events:read", true, "读取当前账户日程详情、日程备注和面试回顾，不包含账户密钥。",
            fields("id", text("日程 ID", 120)), "id");
        register("create_event", "新增日程", "events:write", false,
            "为当前账户投递新增日程。时间为北京时间 YYYY-MM-DD HH:mm；时间点 endsAt 留空，时间段 endsAt 必须晚于 startsAt。notes 仅保存这条日程的备注。",
            eventFields(false), "applicationId", "type", "title", "startsAt");
        register("update_event", "修改日程", "events:write", false,
            "只更新明确提供的字段。notes 仅更新当前日程备注；interviewQuestions 仅更新面试回顾，只有已完成且非错过或放弃的日程可填写回顾，不触发站内 AI 总结。需先读取最新 updatedAt。",
            eventFields(true), "id", "expectedUpdatedAt");
        register("resolve_event", "更新日程状态", "events:write", false,
            "将当前账户日程标记完成、错过、已放弃或恢复待办。必须提供读取时的 updatedAt；请按用户明确要求选择 action。",
            fields("id", text("日程 ID", 120), "expectedUpdatedAt", text("先前读取的 updatedAt", 80),
                "action", choice("complete", "miss", "abandon", "restore")), "id", "expectedUpdatedAt", "action");
    }

    public ArrayNode list(McpTokenStore.Principal principal) {
        ArrayNode list = mapper.createArrayNode();
        for (Tool tool : tools.values()) if (principal.scopes().contains(tool.scope())) list.add(tool.definition());
        return list;
    }

    public JsonNode call(McpTokenStore.Principal principal, String name, JsonNode arguments) throws Exception {
        Tool tool = tools.get(name);
        if (tool == null) throw new ToolException("unknown_tool", "工具不存在");
        if (!principal.scopes().contains(tool.scope())) throw new ToolException("permission_denied", "此令牌未授权该操作，请在网站创建具备相应权限的令牌");
        validate(tool.definition().path("inputSchema"), arguments);
        String target = arguments.path("id").asText("");
        String auditId = tokens.startAudit(principal, name, target);
        try {
            JsonNode result = execute(principal.email(), name, arguments);
            String resultingId = result.path("id").asText(target);
            finishAudit(auditId, "success", resultingId);
            return result;
        } catch (Exception exception) {
            finishAudit(auditId, "failed", target);
            throw exception;
        }
    }

    private JsonNode execute(String email, String name, JsonNode args) throws Exception {
        return switch (name) {
            case "search_applications" -> searchApplications(email, args);
            case "get_application" -> mapper.valueToTree(application(email, args.path("id").asText()));
            case "create_application" -> {
                var result = applications.create(email, applicationInput(args, null), null);
                if (args.path("generateCompanyInsights").asBoolean(false))
                    companies.enqueueAfterApplicationWrite(email, result.application().company(), result.application().position());
                yield mapper.valueToTree(result.application());
            }
            case "update_application" -> {
                ApplicationView current = application(email, args.path("id").asText());
                yield mapper.valueToTree(applications.update(email, current.id(), applicationInput(args, current), args.path("expectedUpdatedAt").asText()).application());
            }
            case "list_events" -> listEvents(email, args);
            case "get_event" -> mapper.valueToTree(event(email, args.path("id").asText()));
            case "create_event" -> mapper.valueToTree(events.create(email, eventInput(args, null)).event());
            case "update_event" -> {
                EventView current = event(email, args.path("id").asText());
                yield mapper.valueToTree(events.update(email, current.id(), eventInput(args, current), args.path("expectedUpdatedAt").asText()).event());
            }
            case "resolve_event" -> mapper.valueToTree(events.resolve(email, args.path("id").asText(),
                Resolution.valueOf(args.path("action").asText().toUpperCase(Locale.ROOT)), args.path("expectedUpdatedAt").asText()).event());
            default -> throw new ToolException("unknown_tool", "工具不存在");
        };
    }

    private JsonNode searchApplications(String email, JsonNode args) throws Exception {
        var page = applications.findApplications(email);
        String query = args.path("query").asText("").toLowerCase(Locale.ROOT).trim();
        String stage = args.path("stage").asText("").trim();
        var rows = page.applications().stream().filter(item -> stage.isEmpty() || stage.equals(item.stage()))
            .filter(item -> (item.company() + " " + item.position() + " " + item.city() + " " + item.notes()).toLowerCase(Locale.ROOT).contains(query)).toList();
        return page(rows, args, page.truncated());
    }

    private JsonNode listEvents(String email, JsonNode args) throws Exception {
        String from = args.path("from").asText("");
        String to = args.path("to").asText("");
        if (!from.isEmpty()) parseTime(from);
        if (!to.isEmpty()) parseTime(to);
        if (!from.isEmpty() && !to.isEmpty() && from.compareTo(to) > 0) throw new IllegalArgumentException("from 不能晚于 to");
        String query = args.path("query").asText("").toLowerCase(Locale.ROOT).trim();
        String appId = args.path("applicationId").asText("");
        String status = args.path("status").asText("all");
        var source = events.findEvents(email);
        var rows = source.events().stream()
            .filter(item -> appId.isEmpty() || appId.equals(item.applicationId()))
            .filter(item -> (item.company() + " " + item.position() + " " + item.title()).toLowerCase(Locale.ROOT).contains(query))
            .filter(item -> status.equals("all") || status.equals(eventStatus(item)))
            .filter(item -> from.isEmpty() || deadline(item).compareTo(from) >= 0)
            .filter(item -> to.isEmpty() || deadline(item).compareTo(to) <= 0)
            .sorted(Comparator.comparing(this::deadline).thenComparing(EventView::id))
            .map(item -> {
                ObjectNode summary = mapper.valueToTree(item);
                summary.remove("notes"); summary.remove("interviewQuestions");
                summary.put("status", eventStatus(item));
                return summary;
            }).toList();
        return page(rows, args, source.truncated());
    }

    private JsonNode page(List<?> rows, JsonNode args, boolean truncated) {
        int offset = args.path("offset").asInt(0), limit = args.path("limit").asInt(20);
        int start = Math.min(offset, rows.size()), end = Math.min(start + limit, rows.size());
        ObjectNode result = mapper.createObjectNode();
        result.set("items", mapper.valueToTree(rows.subList(start, end)));
        result.put("total", rows.size()).put("offset", offset).put("truncated", truncated);
        if (end < rows.size()) result.put("nextOffset", end); else result.putNull("nextOffset");
        return result;
    }
    private String eventStatus(EventView event) {
        return event.abandoned() ? "abandoned" : event.missed() ? "missed" : event.completed() ? "completed" : "pending";
    }
    private String deadline(EventView event) { return event.endsAt() == null || event.endsAt().isBlank() ? event.startsAt() : event.endsAt(); }
    private void parseTime(String text) {
        try { LocalDateTime.parse(text, TIME); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("时间格式必须为 YYYY-MM-DD HH:mm（北京时间）"); }
    }
    private ApplicationView application(String email, String id) throws Exception {
        return applications.findApplications(email).applications().stream().filter(item -> item.id().equals(id)).findFirst()
            .orElseThrow(() -> new ToolException("not_found", "当前账户没有这条投递"));
    }
    private EventView event(String email, String id) throws Exception {
        return events.findEvents(email).events().stream().filter(item -> item.id().equals(id)).findFirst()
            .orElseThrow(() -> new ToolException("not_found", "当前账户没有这条日程"));
    }
    private ApplicationInput applicationInput(JsonNode args, ApplicationView original) {
        JsonNode current = original == null ? mapper.createObjectNode() : mapper.valueToTree(original);
        return new ApplicationInput(value(args, current, "company", ""), value(args, current, "position", ""), value(args, current, "city", ""),
            value(args, current, "channel", "其他"), value(args, current, "appliedDate", LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()),
            value(args, current, "stage", "已投递"), value(args, current, "status", "等待结果"), value(args, current, "notes", ""));
    }
    private EventInput eventInput(JsonNode args, EventView original) {
        JsonNode current = original == null ? mapper.createObjectNode() : mapper.valueToTree(original);
        return new EventInput(value(args, current, "applicationId", ""), value(args, current, "type", "面试"), value(args, current, "title", ""),
            value(args, current, "startsAt", ""), value(args, current, "endsAt", ""), value(args, current, "location", ""),
            value(args, current, "notes", ""), args.has("interviewQuestions") ? args.path("interviewQuestions").asText() : null);
    }
    private String value(JsonNode input, JsonNode original, String key, String fallback) {
        return input.has(key) ? input.path(key).asText() : original.path(key).asText(fallback);
    }

    private ObjectNode applicationFields(boolean update) {
        ObjectNode fields = fields("company", text("公司名称", 120), "position", text("岗位名称", 160), "city", text("城市", 120),
            "channel", text("投递渠道", 80), "appliedDate", text("投递日期 YYYY-MM-DD", 10),
            "stage", choice("已投递", "测评", "笔试", "面试", "Offer", "已结束"),
            "status", choice("等待结果", "已通过", "未通过", "已放弃", "已结束"), "notes", text("仅为岗位备注", 4000));
        if (update) addIdentity(fields);
        else fields.set("generateCompanyInsights", mapper.createObjectNode().put("type", "boolean").put("description", "是否立即触发站内 AI 分类与薪资估算，默认 false"));
        return fields;
    }
    private ObjectNode eventFields(boolean update) {
        ObjectNode fields = fields("applicationId", text("当前账户的关联投递 ID", 120), "type", choice("测评", "笔试", "面试", "Offer", "其他"),
            "title", text("日程名称，例如二面", 120), "startsAt", text("开始时间 YYYY-MM-DD HH:mm", 16),
            "endsAt", text("结束时间，时间点留空", 16), "location", text("地点或视频链接", 1000),
            "notes", text("仅为这条日程的备注", 4000), "interviewQuestions", text("仅为这条日程的面试回顾", 8000));
        if (update) addIdentity(fields);
        return fields;
    }
    private void addIdentity(ObjectNode fields) {
        fields.set("id", text("记录 ID", 120)); fields.set("expectedUpdatedAt", text("读取时返回的 updatedAt", 80));
    }
    private ObjectNode fields(Object... pairs) {
        ObjectNode result = mapper.createObjectNode();
        for (int index = 0; index < pairs.length; index += 2) result.set((String) pairs[index], (JsonNode) pairs[index + 1]);
        return result;
    }
    private ObjectNode text(String description, int max) { return mapper.createObjectNode().put("type", "string").put("description", description).put("maxLength", max); }
    private ObjectNode integer(int min, int max) { return mapper.createObjectNode().put("type", "integer").put("minimum", min).put("maximum", max); }
    private ObjectNode choice(String... choices) {
        ObjectNode result = mapper.createObjectNode().put("type", "string");
        ArrayNode values = result.putArray("enum"); for (String value : choices) values.add(value);
        return result;
    }
    private void register(String name, String title, String scope, boolean readOnly, String description, ObjectNode properties, String... required) {
        ObjectNode definition = mapper.createObjectNode().put("name", name).put("title", title).put("description", description);
        ObjectNode schema = definition.putObject("inputSchema").put("type", "object").put("additionalProperties", false);
        schema.set("properties", properties);
        ArrayNode requiredFields = schema.putArray("required"); for (String field : required) requiredFields.add(field);
        definition.putObject("annotations").put("readOnlyHint", readOnly).put("destructiveHint", !readOnly && !name.startsWith("create_"))
            .put("idempotentHint", readOnly).put("openWorldHint", false);
        tools.put(name, new Tool(scope, definition));
    }
    private void validate(JsonNode schema, JsonNode args) {
        if (args == null || !args.isObject()) throw new IllegalArgumentException("arguments 必须是对象");
        for (JsonNode field : schema.path("required"))
            if (!args.has(field.asText()) || args.path(field.asText()).asText("").isBlank()) throw new IllegalArgumentException("缺少字段：" + field.asText());
        for (var field : args.properties()) {
            JsonNode rule = schema.path("properties").path(field.getKey()), value = field.getValue();
            if (rule.isMissingNode()) throw new IllegalArgumentException("不支持的字段：" + field.getKey());
            String type = rule.path("type").asText();
            if (type.equals("string") && (!value.isTextual() || value.asText().length() > rule.path("maxLength").asInt(16000)))
                throw new IllegalArgumentException("字段格式或长度不正确：" + field.getKey());
            if (type.equals("integer") && (!value.isIntegralNumber() || value.asLong() < rule.path("minimum").asLong() || value.asLong() > rule.path("maximum").asLong()))
                throw new IllegalArgumentException("字段超出整数范围：" + field.getKey());
            if (type.equals("boolean") && !value.isBoolean()) throw new IllegalArgumentException("字段必须为布尔值：" + field.getKey());
            if (rule.path("enum").isArray()) {
                boolean found = false;
                for (JsonNode option : rule.path("enum")) if (option.equals(value)) found = true;
                if (!found) throw new IllegalArgumentException("字段不在允许范围内：" + field.getKey());
            }
        }
    }
    private void finishAudit(String id, String outcome, String target) {
        try { tokens.finishAudit(id, outcome, target); }
        catch (Exception exception) { LOGGER.warn("MCP audit outcome could not be recorded for {}", id, exception); }
    }
    private record Tool(String scope, ObjectNode definition) { }
    public static class ToolException extends RuntimeException {
        public final String code;
        public ToolException(String code, String message) { super(message); this.code = code; }
    }
}
