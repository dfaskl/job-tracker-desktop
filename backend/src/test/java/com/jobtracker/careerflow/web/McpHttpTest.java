package com.jobtracker.careerflow.web;

import com.jobtracker.careerflow.database.AccountService;
import com.jobtracker.careerflow.mcp.McpTokenStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "AI_CALLS_ENABLED=false", "ALLOW_REGISTRATION=true", "SESSION_SECRET=mcp-http-test-session-secret-at-least-32-characters"
})
class McpHttpTest {
    @LocalServerPort int port;
    @Autowired AccountService accounts;
    @Autowired McpTokenStore tokens;
    @Autowired ObjectMapper mapper;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) throws Exception {
        Path directory = Path.of("target", "mcp-tests").toAbsolutePath(); Files.createDirectories(directory);
        String url = "jdbc:sqlite:" + directory.resolve(UUID.randomUUID() + ".db");
        properties.add("APP_DATABASE_URL", () -> url);
    }

    @Test
    void servesStreamableHttpJsonThroughActualSpringHttpConverters() throws Exception {
        accounts.register("http-mcp@example.com", "http-mcp-test-password", "");
        var issued = tokens.create("http-mcp@example.com", "HTTP client", 30, List.of("applications:read", "applications:write"));
        try (HttpClient client = HttpClient.newHttpClient()) {
            var initialized = post(client, issued.token(), "{\"jsonrpc\":\"2.0\",\"id\":\"init\",\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}");
            assertThat(initialized.statusCode()).isEqualTo(200);
            assertThat(initialized.headers().firstValue("Content-Type").orElse("")).contains("application/json");
            assertThat(mapper.readTree(initialized.body()).path("result").path("serverInfo").path("name").asText()).isEqualTo("careerflow");
            var notification = post(client, issued.token(), "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
            assertThat(notification.statusCode()).isEqualTo(202); assertThat(notification.body()).isEmpty();
            var created = post(client, issued.token(), "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"create_application\",\"arguments\":{\"company\":\"HTTP 验证公司\",\"position\":\"后端工程师\"}}}");
            JsonNode result = mapper.readTree(created.body()).path("result");
            assertThat(result.path("isError").asBoolean()).as(created.body()).isFalse();
            assertThat(result.path("structuredContent").path("company").asText()).isEqualTo("HTTP 验证公司");
            assertThat(mapper.readTree(result.path("content").get(0).path("text").asText())).isEqualTo(result.path("structuredContent"));
            tokens.revoke("http-mcp@example.com", issued.metadata().id());
            assertThat(post(client, issued.token(), "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"ping\"}").statusCode()).isEqualTo(401);
        }
    }

    private HttpResponse<String> post(HttpClient client, String secret, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
            .header("Authorization", "Bearer " + secret).header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream").header("MCP-Protocol-Version", "2025-06-18")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
