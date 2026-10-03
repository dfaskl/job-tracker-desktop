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
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewWorkbenchServiceTest {
    @Test
    void classifiesOnlyPositionMetadataAndSummarizesReviewsWithSavedResume() throws Exception {
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
            {"applications":[{"id":"job-1","company":"甲公司","position":"Java开发工程师"}],
             "events":[{"id":"event-1","applicationId":"job-1","type":"面试","title":"一面","completed":true,
               "interviewQuestions":"解释线程池参数"}],"settings":{}}
            """;
        try (var connection = DriverManager.getConnection(jdbc);
             var statement = connection.prepareStatement("UPDATE user_data SET data=? WHERE user_id=(SELECT id FROM users WHERE email=?)")) {
            statement.setString(1, document); statement.setString(2, "reviewer@example.com");
            statement.executeUpdate();
        }
        AiService ai = mock(AiService.class);
        when(ai.classifyInterviewPositions(eq("reviewer@example.com"), any())).thenReturn(mapper.readTree(
            "{\"categories\":[{\"name\":\"后端开发\",\"applicationIds\":[\"job-1\"]}] }"));
        when(ai.summarizeInterviewReviews(eq("reviewer@example.com"), any(), any())).thenReturn(mapper.readTree(
            "{\"topics\":[{\"name\":\"线程池\",\"count\":1,\"kind\":\"knowledge\",\"summary\":\"并发知识\",\"questions\":[\"解释线程池参数\"]}]}"));
        InterviewWorkbenchService service = new InterviewWorkbenchService(environment, applications, mapper, ai);
        service.saveResume("reviewer@example.com", mapper.readTree(
            "{\"internships\":[],\"projects\":[{\"name\":\"项目甲\",\"description\":\"简介\",\"coreWork\":\"核心工作\"}]}"));

        JsonNode classified = service.classify("reviewer@example.com");
        ArgumentCaptor<JsonNode> positions = ArgumentCaptor.forClass(JsonNode.class);
        verify(ai).classifyInterviewPositions(eq("reviewer@example.com"), positions.capture());
        assertThat(positions.getValue().toString()).contains("甲公司", "Java开发工程师").doesNotContain("线程池", "interviewQuestions");
        assertThat(classified.path("classification").path("categories").get(0).path("name").asText()).isEqualTo("后端开发");

        JsonNode summarized = service.summarize("reviewer@example.com", "category-1");
        ArgumentCaptor<JsonNode> reviews = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<JsonNode> resume = ArgumentCaptor.forClass(JsonNode.class);
        verify(ai).summarizeInterviewReviews(eq("reviewer@example.com"), reviews.capture(), resume.capture());
        assertThat(reviews.getValue().toString()).contains("解释线程池参数");
        assertThat(resume.getValue().toString()).contains("项目甲");
        assertThat(summarized.path("summaries").path("category-1").path("topics").get(0).path("name").asText()).isEqualTo("线程池");
        assertThat(summarized.path("summaries").path("category-1").path("stale").asBoolean()).isFalse();

        JsonNode changedResume = service.saveResume("reviewer@example.com", mapper.readTree(
            "{\"internships\":[],\"projects\":[{\"name\":\"项目乙\",\"description\":\"简介\",\"coreWork\":\"核心工作\"}]}"));
        assertThat(changedResume.path("summaries").path("category-1").path("stale").asBoolean()).isTrue();
    }
}
