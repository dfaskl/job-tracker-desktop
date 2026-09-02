package com.jobtracker.migrationpoc.application;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplicationDocumentMutatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ApplicationDocumentMutator mutator = new ApplicationDocumentMutator(
        mapper, Clock.fixed(Instant.parse("2026-09-03T02:15:00Z"), ZoneOffset.UTC)
    );

    @Test
    void createsAnApplicationWithoutChangingOtherBusinessData() throws Exception {
        String source = """
            {"applications":[],"events":[{"id":"event-1"}],"settings":{"theme":"dark"},"futureField":42}
            """;

        var result = mutator.create(source, input("Example", "Engineer", "已投递", "等待结果"));
        JsonNode saved = mapper.readTree(result.documentJson());

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.application().id()).startsWith("app_");
        assertThat(result.application().createdAt()).isEqualTo("2026-09-03 02:15");
        assertThat(saved.path("settings").path("theme").asText()).isEqualTo("dark");
        assertThat(saved.path("futureField").asInt()).isEqualTo(42);
        assertThat(saved.path("events")).hasSize(1);
        assertThat(saved.path("applications").get(0).path("timeline")).hasSize(1);
    }

    @Test
    void updatesKnownFieldsWhilePreservingUnknownFieldsAndHistory() throws Exception {
        String source = """
            {"applications":[{"id":"app-1","company":"Old","position":"Role","stage":"已投递",
            "status":"等待结果","updatedAt":"2026-09-01 10:00","officialUrl":"https://example.com",
            "timeline":[{"id":"old","title":"旧记录"}]}],"events":[],"settings":{}}
            """;

        var result = mutator.update(
            source, "app-1", input("New", "Role", "面试", "已通过"), "2026-09-01 10:00"
        );
        JsonNode updated = mapper.readTree(result.documentJson()).path("applications").get(0);

        assertThat(updated.path("company").asText()).isEqualTo("New");
        assertThat(updated.path("officialUrl").asText()).isEqualTo("https://example.com");
        assertThat(updated.path("timeline")).hasSize(2);
        assertThat(updated.path("timeline").get(0).path("title").asText()).contains("面试", "已通过");
    }

    @Test
    void rejectsAStaleUpdate() {
        String source = """
            {"applications":[{"id":"app-1","updatedAt":"newer"}],"events":[]}
            """;

        assertThrows(ApplicationDocumentMutator.ConflictException.class, () ->
            mutator.update(source, "app-1", input("A", "B", "已投递", "等待结果"), "older")
        );
    }

    @Test
    void deletesOnlyTheApplicationAndItsRelatedEvents() throws Exception {
        String source = """
            {"applications":[{"id":"app-1","updatedAt":"v1"},{"id":"app-2","updatedAt":"v2"}],
            "events":[{"id":"event-1","applicationId":"app-1"},{"id":"event-2","applicationId":"app-2"}],
            "settings":{"kept":true}}
            """;

        var result = mutator.delete(source, "app-1", "v1");
        JsonNode saved = mapper.readTree(result.documentJson());

        assertThat(result.total()).isEqualTo(1);
        assertThat(saved.path("applications").get(0).path("id").asText()).isEqualTo("app-2");
        assertThat(saved.path("events")).hasSize(1);
        assertThat(saved.path("events").get(0).path("id").asText()).isEqualTo("event-2");
        assertThat(saved.path("settings").path("kept").asBoolean()).isTrue();
    }

    @Test
    void validatesRequiredFieldsAndLegacyEnumerations() {
        assertThrows(ApplicationDocumentMutator.ValidationException.class, () ->
            mutator.create("{\"applications\":[],\"events\":[]}", input("", "Role", "已投递", "等待结果"))
        );
        assertThrows(ApplicationDocumentMutator.ValidationException.class, () ->
            mutator.create("{\"applications\":[],\"events\":[]}", input("A", "Role", "未知阶段", "等待结果"))
        );
    }

    private ApplicationDocumentMutator.ApplicationInput input(
        String company, String position, String stage, String status
    ) {
        return new ApplicationDocumentMutator.ApplicationInput(
            company, position, "上海", "官网", "2026-09-03", stage, status, "备注"
        );
    }
}
