package com.jobtracker.careerflow.database;

import com.jobtracker.careerflow.application.ApplicationDocumentMutator;
import com.jobtracker.careerflow.compat.LegacyPasswordVerifier;
import com.jobtracker.careerflow.config.DatabaseSchemaInitializer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewWorkbenchServiceTest {
    @Test
    void summarizesAllReviewsWithoutPositionClassificationAndMarksChangesStale() throws Exception {
        Path directory = Path.of("target", "interview-workbench-tests").toAbsolutePath();
        Files.createDirectories(directory);
        String jdbc = "jdbc:sqlite:" + directory.resolve(UUID.randomUUID() + ".db");
        MockEnvironment environment = new MockEnvironment().withProperty("APP_DATABASE_URL", jdbc)
            .withProperty("ALLOW_REGISTRATION", "true");
        new DatabaseSchemaInitializer(environment).run(null);
        ObjectMapper mapper = new ObjectMapper();
        ApplicationService applications = new ApplicationService(environment, mapper, new ApplicationDocumentMutator(mapper));
        new AccountService(environment, applications, new LegacyPasswordVerifier())
            .register("reviewer@example.com", "correct-horse-battery", "");
        String document = """
            {"applications":[{"id":"job-1","company":"甲公司","position":"Java开发工程师"},
                             {"id":"job-2","company":"乙公司","position":"AI开发工程师"}],
             "events":[{"id":"event-1","applicationId":"job-1","type":"面试","title":"一面","completed":true,
               "interviewQuestions":"解释线程池参数"},
               {"id":"event-2","applicationId":"job-2","type":"面试","title":"二面","completed":true,
               "interviewQuestions":"介绍项目甲的架构"}],
             "settings":{"interviewWorkbench":{"classification":{"categories":[{"name":"旧岗位分类"}]},"summaries":{}}}}
            """;
        try (var connection = DriverManager.getConnection(jdbc);
             var statement = connection.prepareStatement("UPDATE user_data SET data=? WHERE user_id=(SELECT id FROM users WHERE email=?)")) {
            statement.setString(1, document); statement.setString(2, "reviewer@example.com");
            statement.executeUpdate();
        }
        AiService ai = mock(AiService.class);
        when(ai.summarizeInterviewReviews(eq("reviewer@example.com"), any(), any())).thenReturn(mapper.readTree(
            "{\"topics\":[{\"name\":\"线程池\",\"count\":1,\"kind\":\"knowledge\",\"summary\":\"并发知识\",\"questionAnswers\":[{\"question\":\"解释线程池参数\",\"answer\":\"线程池通过核心线程数、最大线程数和任务队列控制并发与资源。\"}]},"
                + "{\"name\":\"项目架构\",\"count\":2,\"kind\":\"project\",\"summary\":\"项目追问\",\"questionAnswers\":[{\"question\":\"介绍项目甲的架构\",\"answer\":\"结合项目甲的核心工作，说明模块边界、数据流和技术取舍。\"}]}]}"));
        InterviewWorkbenchService service = new InterviewWorkbenchService(environment, applications, mapper, ai);
        service.saveResume("reviewer@example.com", mapper.readTree(
            "{\"internships\":[],\"projects\":[{\"name\":\"项目甲\",\"description\":\"简介\",\"coreWork\":\"核心工作\"}]}"));

        JsonNode summarized = service.summarize("reviewer@example.com");
        ArgumentCaptor<JsonNode> reviews = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<JsonNode> resume = ArgumentCaptor.forClass(JsonNode.class);
        verify(ai).summarizeInterviewReviews(eq("reviewer@example.com"), reviews.capture(), resume.capture());
        assertThat(reviews.getValue().toString()).contains("解释线程池参数", "介绍项目甲的架构", "甲公司", "乙公司");
        assertThat(resume.getValue().toString()).contains("项目甲");
        assertThat(summarized.has("classification")).isFalse();
        assertThat(summarized.has("summaries")).isFalse();
        assertThat(summarized.path("overallSummary").path("topics").get(0).path("name").asText()).isEqualTo("项目架构");
        assertThat(summarized.path("overallSummary").path("topics").get(0).path("questionAnswers").get(0).path("answer").asText())
            .contains("项目甲");
        assertThat(summarized.path("overallSummary").path("stale").asBoolean()).isFalse();
        String previousFormatKey = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest((reviews.getValue().toString() + resume.getValue().toString()).getBytes(StandardCharsets.UTF_8)));
        assertThat(summarized.path("overallSummary").path("sourceKey").asText()).isNotEqualTo(previousFormatKey);

        try (var connection = DriverManager.getConnection(jdbc);
             var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE user_data SET data=json_set(data, '$.events[0].interviewQuestions', '解释线程池实现')");
        }
        assertThat(service.state("reviewer@example.com").path("overallSummary").path("stale").asBoolean()).isTrue();

        JsonNode changedResume = service.saveResume("reviewer@example.com", mapper.readTree(
            "{\"internships\":[],\"projects\":[{\"name\":\"项目乙\",\"description\":\"简介\",\"coreWork\":\"核心工作\"}]}"));
        assertThat(changedResume.path("overallSummary").path("stale").asBoolean()).isTrue();
    }
}
