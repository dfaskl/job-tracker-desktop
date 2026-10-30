package com.jobtracker.careerflow.application;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.assertj.core.api.Assertions.assertThat;

class ApplicationStagePolicyTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private ObjectNode data(String stage, String status, String events) throws Exception {
        return (ObjectNode) mapper.readTree("{\"applications\":[{\"id\":\"a\",\"stage\":\"" + stage
            + "\",\"status\":\"" + status + "\",\"updatedAt\":\"original\"}],\"events\":[" + events + "]}");
    }
    private String event(String type, boolean abandoned) {
        return "{\"applicationId\":\"a\",\"type\":\"" + type + "\",\"abandoned\":" + abandoned + "}";
    }
    private void stage(ObjectNode data, String expected) {
        ApplicationStagePolicy.normalize(data);
        assertThat(data.path("applications").get(0).path("stage").asText()).isEqualTo(expected);
        assertThat(data.path("applications").get(0).path("updatedAt").asText()).isEqualTo("original");
    }
    @Test void ranksByLadderInsteadOfInsertionOrder() throws Exception {
        stage(data("测评", "等待结果", event("面试", false) + "," + event("笔试", false) + "," + event("测评", false)), "面试");
    }
    @Test void abandonedExamFallsBackToAssessmentOrApplied() throws Exception {
        stage(data("笔试", "等待结果", event("测评", false) + "," + event("笔试", true)), "测评");
        stage(data("笔试", "等待结果", event("笔试", true)), "已投递");
    }
    @Test void anotherActiveEventKeepsTheSameStage() throws Exception {
        stage(data("笔试", "等待结果", event("笔试", true) + "," + event("笔试", false)), "笔试");
    }
    @Test void otherEventsDoNotCountEvenWhenNamedLikeInterviews() throws Exception {
        stage(data("面试", "等待结果", "{\"applicationId\":\"a\",\"type\":\"其他\",\"title\":\"面试材料提交\"}"), "已投递");
    }
    @Test void completedAndFutureSchedulesBothCountAndOtherApplicationsDoNot() throws Exception {
        stage(data("已投递", "等待结果", "{\"applicationId\":\"a\",\"type\":\"笔试\",\"completed\":true},"
            + "{\"applicationId\":\"other\",\"type\":\"Offer\"}"), "笔试");
    }
    @Test void explicitTerminalResultsArePreserved() throws Exception {
        stage(data("笔试", "未通过", event("面试", false)), "已结束");
        stage(data("面试", "已放弃", event("笔试", false)), "已结束");
        stage(data("Offer", "已通过", ""), "Offer");
    }
    @Test void abandonedOfferDoesNotFreezeEventDerivedProgress() throws Exception {
        stage(data("Offer", "已通过", event("笔试", false) + "," + event("Offer", true)), "笔试");
        ObjectNode manual = data("Offer", "已通过", event("Offer", true));
        ((ObjectNode) manual.path("applications").get(0)).put("stageSource", "manual-result");
        stage(manual, "Offer");
    }
    @Test void normalizationIsIdempotent() throws Exception {
        ObjectNode root = data("面试", "等待结果", event("笔试", false));
        ApplicationStagePolicy.normalize(root);
        ObjectNode once = root.deepCopy();
        ApplicationStagePolicy.normalize(root);
        assertThat(root).isEqualTo(once);
    }
}
