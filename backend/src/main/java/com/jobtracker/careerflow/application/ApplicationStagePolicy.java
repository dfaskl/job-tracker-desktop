package com.jobtracker.careerflow.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fixed progress ladder. Event dates, insertion order and completion time do not rank stages. */
public final class ApplicationStagePolicy {
    private static final List<String> LADDER = List.of("已投递", "测评", "笔试", "面试", "Offer");
    private static final Set<String> ENDED = Set.of("未通过", "已放弃", "已结束");
    private ApplicationStagePolicy() {}

    public static void normalize(JsonNode document) {
        Map<String, Integer> ranks = new HashMap<>();
        Set<String> offerEvents = new java.util.HashSet<>();
        for (JsonNode event : document.path("events")) {
            String id = event.path("applicationId").asText("");
            String type = event.path("type").asText("");
            if ("Offer".equals(type)) offerEvents.add(id);
            if (event.path("abandoned").asBoolean(false)) continue;
            int rank = LADDER.indexOf(type);
            if (rank > 0) ranks.merge(id, rank, Math::max);
        }
        for (JsonNode node : document.path("applications")) {
            if (!(node instanceof ObjectNode application)) continue;
            String stage = application.path("stage").asText("");
            String status = application.path("status").asText("");
            String id = application.path("id").asText("");
            if ("已结束".equals(stage) || ENDED.contains(status)) {
                application.put("stage", "已结束");
                continue;
            }
            String source = application.path("stageSource").asText("");
            boolean offer = "Offer".equals(stage) || "已通过".equals(status);
            // Preserve manually confirmed results, including legacy offers with no Offer event.
            if (offer && ("manual-result".equals(source)
                || (source.isEmpty() && !offerEvents.contains(id)))) {
                application.put("stage", "Offer");
                application.put("status", "已通过");
                application.put("stageSource", "manual-result");
                continue;
            }
            String derived = LADDER.get(ranks.getOrDefault(id, 0));
            application.put("stage", derived);
            application.put("status", "Offer".equals(derived) ? "已通过" : "等待结果");
            application.put("stageSource", "events");
        }
    }
}
