package com.jobtracker.careerflow.database;

import com.jobtracker.careerflow.ai.AiEndpointPolicy;
import com.jobtracker.careerflow.application.ApplicationDocumentMutator;
import com.jobtracker.careerflow.compat.LegacySecretCrypto;
import com.jobtracker.careerflow.compat.LegacySecretCryptoWriter;
import com.jobtracker.careerflow.compat.LegacyPasswordVerifier;
import com.jobtracker.careerflow.config.DatabaseSchemaInitializer;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiServiceTest {
    @Test
    void revealsOnlyTheOwnersSavedKeyAndStopsAfterClearingIt() throws Exception {
        Path directory = Path.of("target", "ai-key-tests").toAbsolutePath();
        Files.createDirectories(directory);
        MockEnvironment environment = new MockEnvironment()
            .withProperty("APP_DATABASE_URL", "jdbc:sqlite:" + directory.resolve(UUID.randomUUID() + ".db"))
            .withProperty("ALLOW_REGISTRATION", "true")
            .withProperty("POC_ENCRYPTION_KEY", "0123456789abcdef0123456789abcdef");
        new DatabaseSchemaInitializer(environment).run(null);
        ObjectMapper mapper = new ObjectMapper();
        ApplicationService applications = new ApplicationService(
            environment, mapper, new ApplicationDocumentMutator(mapper)
        );
        AccountService accounts = new AccountService(environment, applications, new LegacyPasswordVerifier());
        accounts.register("owner@example.com", "correct-horse-battery", "");
        accounts.register("other@example.com", "correct-horse-battery", "");
        ApplicationService sandbox = mock(ApplicationService.class);
        when(sandbox.status()).thenReturn(new ApplicationService.SandboxStatus(true, true, true, "已开启"));
        AiService service = service(environment, sandbox);

        service.saveConfig("owner@example.com", "https://api.deepseek.com", "deepseek-chat", "private-key-1234", false);

        assertThat(service.revealApiKey("owner@example.com")).isEqualTo("private-key-1234");
        assertThat(service.config("owner@example.com").lastFour()).isEqualTo("1234");
        assertThatThrownBy(() -> service.revealApiKey("other@example.com"))
            .isInstanceOf(AiService.AiValidationException.class);

        service.saveConfig("owner@example.com", "https://api.deepseek.com", "deepseek-chat", "", true);
        assertThatThrownBy(() -> service.revealApiKey("owner@example.com"))
            .isInstanceOf(AiService.AiValidationException.class);
    }

    @Test
    void keepsExternalCallsDisabledWithoutEveryExplicitGate() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("POC_ENCRYPTION_KEY", "0123456789abcdef0123456789abcdef")
            .withProperty("POC_AI_CALLS_ENABLED", "true");
        ApplicationService sandbox = mock(ApplicationService.class);
        when(sandbox.status()).thenReturn(new ApplicationService.SandboxStatus(
            false, false, false, "未开启"
        ));

        var status = service(environment, sandbox).status();

        assertThat(status.callsEnabled()).isFalse();
        assertThat(status.sandboxEnabled()).isFalse();
    }

    @Test
    void parsesJsonInsideTheCommonMarkdownEnvelope() throws Exception {
        ApplicationService sandbox = mock(ApplicationService.class);
        when(sandbox.status()).thenReturn(new ApplicationService.SandboxStatus(
            true, true, true, "已开启"
        ));
        AiService service = service(new MockEnvironment(), sandbox);

        var parsed = service.parseModelJson("```json\n{\"company\":\"Example\",\"summary\":\"\"}\n```");

        assertThat(parsed.path("company").asText()).isEqualTo("Example");
    }

    @Test
    void sanitizesDailyQuoteFields() throws Exception {
        ApplicationService sandbox = mock(ApplicationService.class);
        when(sandbox.status()).thenReturn(new ApplicationService.SandboxStatus(true, true, true, "已开启"));
        AiService service = service(new MockEnvironment(), sandbox);

        var quote = service.dailyQuote(new ObjectMapper().readTree(
            "{\"quote\":\"慢一点\\n也没关系\",\"author\":\"朋友\"}"
        ));

        assertThat(quote.quote()).isEqualTo("慢一点 也没关系");
        assertThat(quote.author()).isEqualTo("朋友");
    }
    @Test
    void sanitizesScheduleAdviceFields() throws Exception {
        ApplicationService sandbox = mock(ApplicationService.class);
        when(sandbox.status()).thenReturn(new ApplicationService.SandboxStatus(true, true, true, "已开启"));
        AiService service = service(new MockEnvironment(), sandbox);

        var advice = service.scheduleAdviceResult(new ObjectMapper().readTree(
            "{\"summary\":\"先完成笔试\\n再参加面试\",\"plans\":[\"09:00-10:00 甲公司笔试\"],\"conflicts\":[\"两项安排重叠\"]}"
        ));

        assertThat(advice.path("summary").asText()).isEqualTo("先完成笔试 再参加面试");
        assertThat(advice.path("plans").get(0).asText()).isEqualTo("09:00-10:00 甲公司笔试");
        assertThat(advice.path("conflicts").get(0).asText()).isEqualTo("两项安排重叠");
    }
    @Test
    void keepsRecognizedScheduleTitleEqualToNoticeType() throws Exception {
        ApplicationService sandbox = mock(ApplicationService.class);
        when(sandbox.status()).thenReturn(new ApplicationService.SandboxStatus(true, true, true, "已开启"));
        AiService service = service(new MockEnvironment(), sandbox);

        var result = service.recognition(new ObjectMapper().readTree(
            "{\"noticeType\":\"笔试\",\"scheduleTitle\":\"新石器2027届AICoding后端工程类考试（二）\"}"
        ));

        assertThat(result.noticeType()).isEqualTo("笔试");
        assertThat(result.scheduleTitle()).isEqualTo("笔试");
    }
    @Test
    void buildsJsonModeRequestWithExampleAndRetryFeedback() {
        ApplicationService sandbox = mock(ApplicationService.class);
        when(sandbox.status()).thenReturn(new ApplicationService.SandboxStatus(true, true, true, "已开启"));
        AiService service = service(new MockEnvironment(), sandbox);

        var request = service.recognitionRequestBody(
            "deepseek-chat", "这是一封 AI 面试通知", "2026-10-02 10:30",
            "JSON 缺少字符串字段 position", "{\"company\":\"示例科技\"}"
        );

        assertThat(request.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(request.path("max_tokens").asInt()).isEqualTo(1_000);
        assertThat(request.path("messages").get(0).path("content").asText())
            .contains("输出示例", "AI 面试", "都必须返回测评");
        assertThat(request.path("messages")).hasSize(4);
        assertThat(request.path("messages").get(2).path("role").asText()).isEqualTo("assistant");
        assertThat(request.path("messages").get(3).path("content").asText())
            .contains("上一次识别失败", "JSON 缺少字符串字段 position");
    }

    @Test
    void retriesThreeTimesAndFeedsTheFailureReasonBack() throws Exception {
        ApplicationService sandbox = mock(ApplicationService.class);
        when(sandbox.status()).thenReturn(new ApplicationService.SandboxStatus(true, true, true, "已开启"));
        AiService service = service(new MockEnvironment(), sandbox);
        AtomicInteger calls = new AtomicInteger();
        List<String> reasons = new ArrayList<>();
        List<String> previousOutputs = new ArrayList<>();
        String valid = """
            {"company":"示例科技","position":"Java开发工程师","noticeType":"面试","scheduleTitle":"面试","suggestedStage":"面试","suggestedStatus":"等待结果","startsAt":"2026-10-08 14:30","endsAt":"","location":"","summary":""}
            """;

        var result = service.recognizeWithRetries("真人面试通知", (reason, previousOutput) -> {
            reasons.add(reason);
            previousOutputs.add(previousOutput);
            return calls.incrementAndGet() < 4 ? "{\"company\":\"示例科技\"}" : valid;
        });

        assertThat(calls.get()).isEqualTo(4);
        assertThat(reasons.get(0)).isEmpty();
        assertThat(reasons.subList(1, 4)).allMatch(reason -> reason.contains("JSON 缺少字符串字段"));
        assertThat(previousOutputs.get(1)).contains("示例科技");
        assertThat(result.noticeType()).isEqualTo("面试");
    }

    @Test
    void forcesAiInterviewIntoAssessmentStage() throws Exception {
        ApplicationService sandbox = mock(ApplicationService.class);
        when(sandbox.status()).thenReturn(new ApplicationService.SandboxStatus(true, true, true, "已开启"));
        AiService service = service(new MockEnvironment(), sandbox);
        var modelResult = new ObjectMapper().readTree(
            "{\"noticeType\":\"面试\",\"suggestedStage\":\"面试\",\"suggestedStatus\":\"等待结果\"}"
        );

        var result = service.recognition(modelResult, "请在 48 小时内完成 AI 面试");

        assertThat(result.noticeType()).isEqualTo("测评");
        assertThat(result.scheduleTitle()).isEqualTo("测评");
        assertThat(result.suggestedStage()).isEqualTo("测评");
    }
    private AiService service(MockEnvironment environment, ApplicationService sandbox) {
        ObjectMapper mapper = new ObjectMapper();
        LegacySecretCrypto crypto = new LegacySecretCrypto();
        return new AiService(
            environment,
            sandbox,
            crypto,
            new LegacySecretCryptoWriter(crypto),
            new AiEndpointPolicy(environment),
            mapper
        );
    }

    @Test
    void disablesDeepSeekThinkingForInterviewSummaryAndExplainsTruncatedResponses() throws Exception {
        ApplicationService sandbox = mock(ApplicationService.class);
        when(sandbox.status()).thenReturn(new ApplicationService.SandboxStatus(true, true, true, "已开启"));
        AiService service = service(new MockEnvironment(), sandbox);
        ObjectMapper mapper = new ObjectMapper();
        var request = service.reviewSummaryRequestBody("", mapper.createArrayNode(), mapper.createObjectNode());
        service.prepareInterviewAnalysisRequest(request, URI.create("https://api.deepseek.com/chat/completions"), "deepseek-v4-flash");
        assertThat(request.path("thinking").path("type").asText()).isEqualTo("disabled");
        assertThat(request.path("response_format").path("type").asText()).isEqualTo("json_object");
        var otherProvider = service.reviewSummaryRequestBody("", mapper.createArrayNode(), mapper.createObjectNode());
        service.prepareInterviewAnalysisRequest(otherProvider, URI.create("https://api.openai.com/v1/chat/completions"), "test-model");
        assertThat(otherProvider.has("thinking")).isFalse();
        assertThatThrownBy(() -> service.parseInterviewAnalysisResponse(mapper.readTree(
            "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"\"}}]}")))
            .isInstanceOf(AiService.AiResponseException.class).hasMessageContaining("截断");
        assertThat(service.parseInterviewAnalysisResponse(mapper.readTree(
            "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"topics\\\":[]}\"}}]}"))
            .path("topics").isArray()).isTrue();
    }

    @Test
    void requestsBroadInterviewTopicsWithQuestionFrequency() {
        ApplicationService sandbox = mock(ApplicationService.class);
        when(sandbox.status()).thenReturn(new ApplicationService.SandboxStatus(true, true, true, "已开启"));
        AiService service = service(new MockEnvironment(), sandbox);
        var request = service.reviewSummaryRequestBody("", new ObjectMapper().createArrayNode(), new ObjectMapper().createObjectNode());
        String instruction = request.path("messages").get(0).path("content").asText();
        assertThat(instruction).contains("宽泛主题", "八股主题最多 8 个", "每个原始问题只计入一个", "覆盖的原始问题数",
            "全部不同原始问题", "不得只选代表题", "供用户复习、理解和自学", "分步骤解释核心原理", "结合简历", "questionAnswers",
            "每一段实习和每一个项目各自成为一个独立类别", "resumeRef", "正好有 3 个类别");
        assertThat(request.path("max_tokens").asInt()).isEqualTo(16_000);
        var resumeRequest = service.reviewSummaryRequestBody("", new ObjectMapper().createArrayNode(),
            new ObjectMapper().readTree("{\"internships\":[{\"company\":\"甲公司\",\"role\":\"实习生\"}],\"projects\":[{\"name\":\"项目甲\"}]}"));
        String input = resumeRequest.path("messages").get(1).path("content").asText();
        assertThat(input).contains("internship-1", "project-1");
    }
}
