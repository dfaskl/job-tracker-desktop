package com.jobtracker.careerflow.database;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IncrementalInterviewJsonParserTest {
    @Test
    void emitsOnlyCompleteQuestionAnswersAndHandlesChunkBoundariesAndBracesInStrings() {
        ObjectMapper mapper = new ObjectMapper();
        List<JsonNode> questions = new ArrayList<>();
        IncrementalInterviewJsonParser parser = new IncrementalInterviewJsonParser(mapper, questions::add);

        parser.accept("{\"topics\":[{\"name\":\"并发\",\"questionAnswers\":[{\"question\":\"JSON 中的 { 和 ");
        assertThat(questions).isEmpty();
        parser.accept("} 要保留吗？\",\"answer\":\"要，\\\"字符串\\\"中的大括号 { } 不代表结构结束。\"},{\"question\":\"线程池是什么？\"");
        assertThat(questions).hasSize(1);
        assertThat(questions.getFirst().path("question").asText()).isEqualTo("JSON 中的 { 和 } 要保留吗？");
        parser.accept(",\"answer\":\"线程池用于复用线程。\"}]}]}");

        assertThat(questions).hasSize(2);
        assertThat(questions.getLast().path("question").asText()).isEqualTo("线程池是什么？");
    }
}
