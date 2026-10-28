package com.jobtracker.careerflow.web;

import com.jobtracker.careerflow.application.ApplicationDocumentMutator;
import com.jobtracker.careerflow.compat.LegacyPasswordVerifier;
import com.jobtracker.careerflow.config.DatabaseSchemaInitializer;
import com.jobtracker.careerflow.database.*;
import com.jobtracker.careerflow.event.EventDocumentMutator;
import com.jobtracker.careerflow.mcp.McpTokenStore;
import com.jobtracker.careerflow.mcp.McpTools;
import com.jobtracker.careerflow.web.AuthController;
import com.jobtracker.careerflow.web.McpController;
import com.jobtracker.careerflow.web.McpTokenController;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpIntegrationTest {
    private static final String OWNER = "mcp-owner@example.com", OTHER = "mcp-other@example.com";
    private static final List<String> FULL = List.of("applications:read", "applications:write", "events:read", "events:write");

    @Test
    void issuesHashedAccountBoundTokensAndEnforcesRevocationExpiryAndDisabledAccounts() throws Exception {
        Fixture f = fixture();
        var issued = f.tokens.create(OWNER, "我的 Codex", 90, List.of("events:write"));
        assertThat(issued.metadata().scopes()).containsExactlyInAnyOrder("events:read", "events:write");
        assertThat(issued.token()).startsWith("cflow_mcp_").hasSize(53);
        assertThat(f.tokens.authenticate(issued.token())).isPresent();
        assertThat(f.mapper.writeValueAsString(f.tokens.list(OWNER))).doesNotContain(issued.token(), "token_hash");
        assertThat(f.tokens.list(OTHER)).isEmpty();
        assertThat(f.tokens.revoke(OTHER, issued.metadata().id())).isFalse();
        try (var connection = DriverManager.getConnection(f.url); var select = connection.prepareStatement("SELECT token_hash FROM mcp_tokens WHERE id=?")) {
            select.setString(1, issued.metadata().id());
            try (var row = select.executeQuery()) { row.next(); assertThat(row.getString(1)).hasSize(64).doesNotContain(issued.token()); }
        }
        assertThat(f.tokens.revoke(OWNER, issued.metadata().id())).isTrue();
        assertThat(f.tokens.authenticate(issued.token())).isEmpty();
        var expired = f.tokens.create(OWNER, "expired", 1, List.of("events:read"));
        try (var connection = DriverManager.getConnection(f.url); var update = connection.prepareStatement("UPDATE mcp_tokens SET expires_at=1 WHERE id=?")) {
            update.setString(1, expired.metadata().id()); update.executeUpdate();
        }
        assertThat(f.tokens.authenticate(expired.token())).isEmpty();
        var disabled = f.tokens.create(OTHER, "disabled", 1, List.of("events:read"));
        try (var connection = DriverManager.getConnection(f.url); var update = connection.prepareStatement("UPDATE users SET disabled_at=1 WHERE lower(email)=?")) {
            update.setString(1, OTHER); update.executeUpdate();
        }
        assertThat(f.tokens.authenticate(disabled.token())).isEmpty();
        assertThatThrownBy(() -> f.tokens.create(OWNER, "invalid", 366, FULL)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.tokens.create(OWNER, "invalid", 30, List.of("admin"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negotiatesProtocolRequiresBearerAndRejectsUnsafeRequestsAndHiddenWrites() throws Exception {
        Fixture f = fixture();
        String secret = f.tokens.create(OWNER, "reader", 30, List.of("applications:read")).token();
        var initialize = rpc(f, secret, "initialize", f.mapper.readTree("{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}"));
        assertThat(json(initialize).path("result").path("protocolVersion").asText()).isEqualTo("2025-06-18");
        assertThat(json(initialize).path("result").path("capabilities").path("tools").isObject()).isTrue();
        assertThat(rpc(f, "", "ping", f.mapper.createObjectNode()).getStatusCode().value()).isEqualTo(401);
        var hostile = request(secret, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}");
        hostile.addHeader("Origin", "http://evil.example");
        assertThat(f.controller.handle(hostile).getStatusCode().value()).isEqualTo(403);
        var get = request(secret, ""); get.setMethod("GET");
        assertThat(f.controller.handle(get).getStatusCode().value()).isEqualTo(405);
        var unsupported = request(secret, "{}"); unsupported.addHeader("MCP-Protocol-Version", "not-supported");
        assertThat(f.controller.handle(unsupported).getStatusCode().value()).isEqualTo(400);
        assertThat(f.controller.handle(request(secret, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}")).getStatusCode().value()).isEqualTo(202);
        assertThat(json(f.controller.handle(request(secret, "{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{\"name\":\"create_application\"}}"))).path("error").path("code").asInt()).isEqualTo(-32600);
        String tools = json(rpc(f, secret, "tools/list", f.mapper.createObjectNode())).toString();
        assertThat(tools).contains("search_applications", "get_application").doesNotContain("create_application", "list_events");
        var denied = call(f, secret, "create_application", "{\"company\":\"X\",\"position\":\"Y\"}");
        assertThat(denied.path("isError").asBoolean()).isTrue();
        assertThat(denied.path("structuredContent").path("code").asText()).isEqualTo("permission_denied");
        assertThat(f.applications.findApplications(OWNER).total()).isZero();
    }

    @Test
    void writesThroughBusinessServicesWithoutCrossAccountOrCrossRecordChanges() throws Exception {
        Fixture f = fixture();
        String secret = f.tokens.create(OWNER, "writer", 90, FULL).token();
        var other = f.applications.create(OTHER, new ApplicationDocumentMutator.ApplicationInput("私有公司", "私有岗位", "", "官网", "2026-10-29", "已投递", "等待结果", "其他账户备注"), null).application();
        JsonNode created = data(call(f, secret, "create_application", "{\"company\":\"测试公司\",\"position\":\"后端工程师\",\"notes\":\"岗位备注\"}"));
        String appId = created.path("id").asText();
        assertThat(appId).isNotBlank();
        verifyNoInteractions(f.companies);
        var appUpdate = f.mapper.createObjectNode().put("id", appId).put("expectedUpdatedAt", created.path("updatedAt").asText()).put("city", "上海");
        JsonNode appChanged = data(call(f, secret, "update_application", appUpdate.toString()));
        assertThat(appChanged.path("company").asText()).isEqualTo("测试公司");
        assertThat(appChanged.path("position").asText()).isEqualTo("后端工程师");
        assertThat(appChanged.path("notes").asText()).isEqualTo("岗位备注");
        var foreign = call(f, secret, "get_application", "{\"id\":\"" + other.id() + "\"}");
        assertThat(foreign.path("structuredContent").path("code").asText()).isEqualTo("not_found");
        var foreignUpdate = f.mapper.createObjectNode().put("id", other.id()).put("expectedUpdatedAt", other.updatedAt()).put("notes", "不可写入");
        assertThat(call(f, secret, "update_application", foreignUpdate.toString()).path("structuredContent").path("code").asText()).isEqualTo("not_found");
        var otherEvent = f.events.create(OTHER, new EventDocumentMutator.EventInput(other.id(), "面试", "私有日程", "2026-11-03 10:00", "", "", "私有备注")).event();
        foreignUpdate.put("id", otherEvent.id()).put("expectedUpdatedAt", otherEvent.updatedAt());
        assertThat(call(f, secret, "update_event", foreignUpdate.toString()).path("structuredContent").path("code").asText()).isEqualTo("not_found");
        var invalid = call(f, secret, "search_applications", "{\"email\":\"" + OTHER + "\"}");
        assertThat(invalid.path("isError").asBoolean()).isTrue();
        JsonNode visible = data(call(f, secret, "search_applications", "{}"));
        assertThat(visible.path("total").asInt()).isEqualTo(1);
        assertThat(visible.toString()).doesNotContain("私有公司", "其他账户备注");
        JsonNode event1 = data(call(f, secret, "create_event", "{\"applicationId\":\""+appId+"\",\"type\":\"面试\",\"title\":\"一面\",\"startsAt\":\"2026-11-01 15:00\",\"notes\":\"第一场备注\"}"));
        JsonNode event2 = data(call(f, secret, "create_event", "{\"applicationId\":\""+appId+"\",\"type\":\"笔试\",\"title\":\"笔试\",\"startsAt\":\"2026-10-29 09:00\",\"endsAt\":\"2026-11-02 12:00\",\"notes\":\"第二场备注\"}"));
        var completion = f.mapper.createObjectNode().put("id", event1.path("id").asText())
            .put("expectedUpdatedAt", event1.path("updatedAt").asText()).put("action", "complete");
        event1 = data(call(f, secret, "resolve_event", completion.toString()));
        var updateArgs = f.mapper.createObjectNode().put("id", event1.path("id").asText()).put("expectedUpdatedAt", event1.path("updatedAt").asText())
            .put("notes", "仅修改第一场").put("interviewQuestions", "谈谈线程池");
        JsonNode changed = data(call(f, secret, "update_event", updateArgs.toString()));
        assertThat(changed.path("notes").asText()).isEqualTo("仅修改第一场");
        assertThat(data(call(f, secret, "get_application", "{\"id\":\""+appId+"\"}")).path("notes").asText()).isEqualTo("岗位备注");
        assertThat(data(call(f, secret, "get_event", "{\"id\":\""+event2.path("id").asText()+"\"}")).path("notes").asText()).isEqualTo("第二场备注");
        updateArgs.put("expectedUpdatedAt", "stale");
        assertThat(call(f, secret, "update_event", updateArgs.toString()).path("structuredContent").path("code").asText()).isEqualTo("conflict");
        JsonNode ordered = data(call(f, secret, "list_events", "{\"status\":\"all\"}"));
        assertThat(ordered.path("items").get(0).path("id").asText()).isEqualTo(event1.path("id").asText());
        assertThat(ordered.toString()).doesNotContain("仅修改第一场", "谈谈线程池");
        var resolution = f.mapper.createObjectNode().put("id", changed.path("id").asText()).put("expectedUpdatedAt", changed.path("updatedAt").asText()).put("action", "restore");
        JsonNode restored = data(call(f, secret, "resolve_event", resolution.toString()));
        assertThat(restored.path("completed").asBoolean()).isFalse();
        var notesOnly = f.mapper.createObjectNode().put("id", restored.path("id").asText())
            .put("expectedUpdatedAt", restored.path("updatedAt").asText()).put("notes", "恢复后只改备注");
        assertThat(data(call(f, secret, "update_event", notesOnly.toString())).path("interviewQuestions").asText()).isEqualTo("谈谈线程池");
        assertThat(f.applications.findApplications(OTHER).applications().getFirst().notes()).isEqualTo("其他账户备注");
        assertThat(f.events.findEvents(OTHER).events().getFirst().notes()).isEqualTo("私有备注");
        assertThat(f.tokens.audit(OWNER)).anyMatch(entry -> entry.tool().equals("update_event") && entry.outcome().equals("success"));
        assertThat(f.mapper.writeValueAsString(f.tokens.audit(OWNER))).doesNotContain(secret, "谈谈线程池", "仅修改第一场");
    }

    @Test
    void managesTokensOnlyWithBrowserSessionAndValidOrigin() throws Exception {
        Fixture f = fixture();
        AuthController auth = mock(AuthController.class);
        when(auth.authenticatedUser("owner-session")).thenReturn(Optional.of(f.accounts.findByEmail(OWNER).orElseThrow()));
        when(auth.authenticatedUser("other-session")).thenReturn(Optional.of(f.accounts.findByEmail(OTHER).orElseThrow()));
        McpTokenController controller = new McpTokenController(auth, f.tokens);
        var created = controller.create("owner-session", new McpTokenController.CreateRequest("Codex", 30, FULL), request("", ""));
        var issued = (McpTokenStore.IssuedToken) created.getBody();
        assertThat(issued).isNotNull();
        assertThat(f.mapper.writeValueAsString(controller.list("owner-session").getBody())).doesNotContain(issued.token());
        controller.revoke("other-session", issued.metadata().id(), request("", ""));
        assertThat(f.tokens.authenticate(issued.token())).isPresent();
        var hostile = request("", ""); hostile.addHeader("Origin", "http://evil.example");
        assertThat(controller.create("owner-session", new McpTokenController.CreateRequest("Bad", 30, FULL), hostile).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.list("unknown").getStatusCode().value()).isEqualTo(401);
        controller.revoke("owner-session", issued.metadata().id(), request("", ""));
        assertThat(f.tokens.authenticate(issued.token())).isEmpty();
    }

    private JsonNode call(Fixture f, String secret, String name, String args) throws Exception {
        var params = f.mapper.createObjectNode().put("name", name); params.set("arguments", f.mapper.readTree(args));
        return json(rpc(f, secret, "tools/call", params)).path("result");
    }
    private JsonNode data(JsonNode result) { assertThat(result.path("isError").asBoolean()).as(result.toString()).isFalse(); return result.path("structuredContent"); }
    private ResponseEntity<?> rpc(Fixture f, String secret, String method, JsonNode params) throws Exception {
        var message = f.mapper.createObjectNode().put("jsonrpc", "2.0").put("id", 1).put("method", method); message.set("params", params);
        return f.controller.handle(request(secret, f.mapper.writeValueAsString(message)));
    }
    private JsonNode json(ResponseEntity<?> response) { assertThat(response.getStatusCode().value()).isEqualTo(200); return (JsonNode) response.getBody(); }
    private MockHttpServletRequest request(String secret, String json) {
        var request = new MockHttpServletRequest("POST", "/mcp");
        request.setScheme("http"); request.addHeader("Host", "localhost");
        request.setContentType("application/json"); request.addHeader("Accept", "application/json, text/event-stream");
        if (!secret.isEmpty()) request.addHeader("Authorization", "Bearer " + secret);
        request.setContent(json.getBytes(StandardCharsets.UTF_8));
        return request;
    }
    private Fixture fixture() throws Exception {
        Path directory = Path.of("target", "mcp-tests").toAbsolutePath(); Files.createDirectories(directory);
        String url = "jdbc:sqlite:" + directory.resolve(UUID.randomUUID() + ".db");
        var env = new MockEnvironment().withProperty("APP_DATABASE_URL", url).withProperty("ALLOW_REGISTRATION", "true");
        new DatabaseSchemaInitializer(env).run(null);
        var mapper = new ObjectMapper();
        var applications = new ApplicationService(env, mapper, new ApplicationDocumentMutator(mapper));
        var accounts = new AccountService(env, applications, new LegacyPasswordVerifier());
        accounts.register(OWNER, "mcp-owner-password", ""); accounts.register(OTHER, "mcp-other-password", "");
        var events = new EventService(env, applications, new EventDocumentMutator(mapper));
        var tokens = new McpTokenStore(env);
        var companies = mock(CompanyIndustryService.class);
        var tools = new McpTools(applications, events, companies, tokens, mapper);
        return new Fixture(url, mapper, applications, accounts, events, tokens, companies, new McpController(tokens, tools, mapper));
    }
    private record Fixture(String url, ObjectMapper mapper, ApplicationService applications, AccountService accounts, EventService events,
                           McpTokenStore tokens, CompanyIndustryService companies, McpController controller) { }
}
