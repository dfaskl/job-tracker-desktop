package com.jobtracker.careerflow.database;

import com.jobtracker.careerflow.ai.AiEndpointPolicy;
import com.jobtracker.careerflow.compat.LegacySecretCrypto;
import com.jobtracker.careerflow.compat.LegacySecretCryptoWriter;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiServiceTest {
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
}
