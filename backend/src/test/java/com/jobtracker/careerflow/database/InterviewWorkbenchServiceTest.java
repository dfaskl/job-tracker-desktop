package com.jobtracker.careerflow.database;

import com.jobtracker.careerflow.application.ApplicationDocumentMutator;
import com.jobtracker.careerflow.compat.LegacyPasswordVerifier;
import com.jobtracker.careerflow.config.DatabaseSchemaInitializer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

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
    void classifiesReviewsInSavedBatchesThenStreamsAndPersistsAnswersBySmallBatches() throws Exception {
        Path directory = Path.of("target", "interview-workbench-tests").toAbsolutePath();
        Files.createDirectories(directory);
        String jdbc = "jdbc:sqlite:" + directory.resolve(UUID.randomUUID() + ".db");
        MockEnvironment environment = new MockEnvironment().withProperty("APP_DATABASE_URL", jdbc)
            .withProperty("ALLOW_REGISTRATION", "true");
        new DatabaseSchemaInitializer(environment).run(null);
        ObjectMapper mapper = new ObjectMapper();
        ApplicationService applications = new ApplicationService(environment, mapper, new ApplicationDocumentMutator(mapper));
        new AccountService(environment, applications, new LegacyPasswordVerifier()).register("batch@example.com", "correct-horse-battery", "");
        String longReview = "解释线程池的核心线程数、最大线程数、队列和拒绝策略。" + "面试追问背景。".repeat(900);
        String document = "{\"applications\":[{\"id\":\"job\",\"company\":\"甲公司\",\"position\":\"Java 工程师\"}],\"events\":["
            + "{\"id\":\"e1\",\"applicationId\":\"job\",\"type\":\"面试\",\"title\":\"一面\",\"completed\":true,\"interviewQuestions\":" + mapper.writeValueAsString(longReview) + "},"
            + "{\"id\":\"e2\",\"applicationId\":\"job\",\"type\":\"面试\",\"title\":\"二面\",\"completed\":true,\"interviewQuestions\":" + mapper.writeValueAsString(longReview) + "},"
            + "{\"id\":\"e3\",\"applicationId\":\"job\",\"type\":\"面试\",\"title\":\"三面\",\"completed\":true,\"interviewQuestions\":" + mapper.writeValueAsString(longReview) + "}]}";
        try (var connection = DriverManager.getConnection(jdbc);
             var statement = connection.prepareStatement("UPDATE user_data SET data=? WHERE user_id=(SELECT id FROM users WHERE email=?)")) {
            statement.setString(1, document); statement.setString(2, "batch@example.com"); statement.executeUpdate();
        }
        AiService ai = mock(AiService.class);
        when(ai.classifyInterviewReviewBatch(eq("batch@example.com"), any(), any())).thenAnswer(invocation -> {
            JsonNode batch = invocation.getArgument(1);
            var topics = mapper.createObjectNode().putArray("topics");
            for (JsonNode review : batch) {
                ObjectNode topic = topics.addObject().put("name", "线程池与并发").put("kind", "knowledge")
                    .put("summary", "线程池并发控制");
                var questions = topic.putArray("questions");
                questions.addObject().put("question", "解释线程池参数").put("eventId", review.path("eventId").asText());
                questions.addObject().put("question", "如何验证线程池边界：" + review.path("title").asText()).put("eventId", review.path("eventId").asText());
            }
            return topics.size() == 0 ? mapper.createObjectNode() : mapper.createObjectNode().set("topics", topics);
        });
        when(ai.streamInterviewAnswerBatch(eq("batch@example.com"), any(), any(), any(), any())).thenAnswer(invocation -> {
            JsonNode questions = invocation.getArgument(2);
            StringBuilder json = new StringBuilder("{\"questionAnswers\":[");
            for (int i = 0; i < questions.size(); i++) {
                if (i > 0) json.append(',');
                json.append(mapper.writeValueAsString(mapper.createObjectNode().put("question", questions.get(i).path("question").asText())
                    .put("answer", "完整的线程池复习讲解与实践细节。")));
            }
            json.append("]}"); String output = json.toString();
            invocation.<java.util.function.Consumer<String>>getArgument(4).accept(output);
            return mapper.readTree(output);
        });
        InterviewWorkbenchService service = new InterviewWorkbenchService(environment, applications, mapper, ai);
        var streamed = new java.util.ArrayList<ObjectNode>();
        JsonNode result = service.summarizeStreaming("batch@example.com", streamed::add);
        verify(ai, org.mockito.Mockito.times(3)).classifyInterviewReviewBatch(eq("batch@example.com"), any(), any());
        verify(ai, org.mockito.Mockito.times(2)).streamInterviewAnswerBatch(eq("batch@example.com"), any(), any(), any(), any());
        assertThat(result.path("overallSummary").path("topics").get(0).path("count").asInt()).isEqualTo(6);
        assertThat(result.path("overallSummary").path("topics").get(0).path("questionAnswers").size()).isEqualTo(4);
        assertThat(result.path("overallSummary").path("topics").get(0).path("questionAnswers").get(0).path("frequency").asInt()).isEqualTo(3);
        assertThat(result.path("overallSummary").path("topics").get(0).path("questionAnswers").get(0).path("answerStatus").asText()).isEqualTo("completed");
        assertThat(result.path("summaryJob").path("stage").asText()).isEqualTo("completed");
        assertThat(streamed).anyMatch(event -> event.path("type").asText().equals("classified"));
        assertThat(streamed).anyMatch(event -> event.path("type").asText().equals("question"));
    }

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
               "interviewQuestions":"解释线程池参数\\n请做一个自我介绍"},
               {"id":"event-2","applicationId":"job-2","type":"面试","title":"二面","completed":true,
               "interviewQuestions":"介绍项目甲的架构\\nMQTT 接收报文后如何处理\\nCSV 映射和位域解码如何实现\\n如何验证 16-bit 小端解析\\n如何隔离不同厂商协议"}],
             "settings":{"interviewWorkbench":{"classification":{"categories":[{"name":"旧岗位分类"}]},"summaries":{}}}}
            """;
        try (var connection = DriverManager.getConnection(jdbc);
             var statement = connection.prepareStatement("UPDATE user_data SET data=? WHERE user_id=(SELECT id FROM users WHERE email=?)")) {
            statement.setString(1, document); statement.setString(2, "reviewer@example.com");
            statement.executeUpdate();
        }
        AiService ai = mock(AiService.class);
        when(ai.classifyInterviewReviewBatch(eq("reviewer@example.com"), any(), any())).thenReturn(mapper.readTree(
            "{\"topics\":[{\"name\":\"Java 并发与线程池\",\"kind\":\"knowledge\",\"summary\":\"并发知识\",\"questions\":[{\"question\":\"解释线程池参数\",\"eventId\":\"event-1\"}]},"
                + "{\"name\":\"自我介绍与动机\",\"kind\":\"other\",\"summary\":\"个人经历表达\",\"questions\":[{\"question\":\"请做一个自我介绍\",\"eventId\":\"event-1\"}]},"
                + "{\"name\":\"项目甲\",\"resumeRef\":\"project-1\",\"kind\":\"project\",\"summary\":\"项目追问\",\"questions\":["
                + "{\"question\":\"介绍项目甲的架构\",\"eventId\":\"event-2\"},"
                + "{\"question\":\"MQTT 接收报文后如何处理\",\"eventId\":\"event-2\"},"
                + "{\"question\":\"CSV 映射和位域解码如何实现\",\"eventId\":\"event-2\"},"
                + "{\"question\":\"如何验证 16-bit 小端解析\",\"eventId\":\"event-2\"},"
                + "{\"question\":\"如何隔离不同厂商协议\",\"eventId\":\"event-2\"}]}]}"));
        when(ai.streamInterviewAnswerBatch(eq("reviewer@example.com"), any(), any(), any(), any())).thenAnswer(invocation -> {
            JsonNode batch = invocation.getArgument(2);
            ObjectNode response = mapper.createObjectNode(); var generated = response.putArray("questionAnswers");
            for (JsonNode question : batch) generated.addObject().put("question", question.path("question").asText())
                .put("answer", "结合项目甲与核心原理、执行流程、方案取舍和验证方法展开的完整学习讲解，并说明关键实现边界与实践细节。");
            String output = response.toString(); invocation.<java.util.function.Consumer<String>>getArgument(4).accept(output);
            return response;
        });
        InterviewWorkbenchService service = new InterviewWorkbenchService(environment, applications, mapper, ai);
        service.saveResume("reviewer@example.com", mapper.readTree(
            "{\"internships\":[{\"company\":\"实习公司\",\"role\":\"Java 实习生\",\"description\":\"实习简介\",\"coreWork\":\"实习工作\"}],"
                + "\"projects\":[{\"name\":\"项目甲\",\"description\":\"简介\",\"coreWork\":\"核心工作\"},"
                + "{\"name\":\"项目乙\",\"description\":\"简介乙\",\"coreWork\":\"核心工作乙\"}]}"));

        JsonNode summarized = service.summarize("reviewer@example.com");
        ArgumentCaptor<JsonNode> reviews = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<JsonNode> resume = ArgumentCaptor.forClass(JsonNode.class);
        verify(ai).classifyInterviewReviewBatch(eq("reviewer@example.com"), reviews.capture(), resume.capture());
        assertThat(reviews.getValue().toString()).contains("解释线程池参数", "介绍项目甲的架构", "甲公司", "乙公司");
        assertThat(resume.getValue().toString()).contains("项目甲");
        assertThat(resume.getValue().path("projects").get(0).path("name").asText()).isEqualTo("项目甲");
        assertThat(summarized.has("classification")).isFalse();
        assertThat(summarized.has("summaries")).isFalse();
        assertThat(summarized.path("overallSummary").path("topics").get(0).path("name").asText()).isEqualTo("项目甲");
        assertThat(summarized.path("overallSummary").path("topics").get(0).path("questionAnswers").get(0).path("answer").asText())
            .contains("项目甲");
        assertThat(summarized.path("overallSummary").path("topics").get(0).path("questionAnswers").size()).isEqualTo(5);
        assertThat(summarized.path("overallSummary").path("topics").toString())
            .contains("\"kind\":\"other\"", "\"kind\":\"knowledge\"", "\"kind\":\"project\"");
        JsonNode projectTopics = mapper.createArrayNode();
        for (JsonNode topic : summarized.path("overallSummary").path("topics")) {
            if (topic.path("kind").asText().equals("project")) ((tools.jackson.databind.node.ArrayNode) projectTopics).add(topic);
        }
        assertThat(projectTopics.size()).isEqualTo(3);
        assertThat(projectTopics.get(0).path("name").asText()).isEqualTo("项目甲");
        assertThat(projectTopics.get(0).path("count").asInt()).isEqualTo(5);
        assertThat(projectTopics.get(1).path("name").asText()).isEqualTo("实习公司 · Java 实习生（实习）");
        assertThat(projectTopics.get(2).path("name").asText()).isEqualTo("项目乙");
        assertThat(projectTopics.get(2).path("count").asInt()).isZero();
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
