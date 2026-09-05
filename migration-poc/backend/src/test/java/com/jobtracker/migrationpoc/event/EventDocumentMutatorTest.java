package com.jobtracker.migrationpoc.event;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventDocumentMutatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final EventDocumentMutator mutator = new EventDocumentMutator(
        mapper, Clock.fixed(Instant.parse("2026-09-03T06:30:00Z"), ZoneOffset.UTC)
    );

    @Test
    void createsARangeEventAndAdvancesApplicationProgress() throws Exception {
        var result = mutator.create(document(), input("面试", "一面", "2026-09-05 09:00", "2026-09-05 10:00"));
        JsonNode saved = mapper.readTree(result.documentJson());
        JsonNode application = saved.path("applications").get(0);
        JsonNode event = saved.path("events").get(0);

        assertThat(result.total()).isEqualTo(1);
        assertThat(event.path("endsAt").asText()).isEqualTo("2026-09-05 10:00");
        assertThat(event.path("company").asText()).isEqualTo("Example");
        assertThat(application.path("stage").asText()).isEqualTo("面试");
        assertThat(application.path("status").asText()).isEqualTo("等待结果");
        assertThat(application.path("timeline").get(0).path("eventId").asText()).isEqualTo(result.event().id());
        assertThat(application.path("futureField").asInt()).isEqualTo(7);
    }

    @Test
    void requiresTheRangeEndToBeAfterTheStart() {
        assertThrows(EventDocumentMutator.ValidationException.class, () ->
            mutator.create(document(), input("笔试", "在线笔试", "2026-09-05 10:00", "2026-09-05 09:00"))
        );
    }

    @Test
    void completesAndRestoresARangeUsingTheActualCompletionTime() throws Exception {
        var created = mutator.create(document(), input("测评", "在线测评", "2026-09-01 09:00", "2026-09-10 18:00"));
        var completed = mutator.resolve(
            created.documentJson(), created.event().id(), EventDocumentMutator.Resolution.COMPLETE,
            created.event().updatedAt()
        );

        assertThat(completed.event().completed()).isTrue();
        assertThat(completed.event().missed()).isFalse();
        assertThat(completed.event().completedAt()).isEqualTo("2026-09-03 06:30");
        assertThat(completed.event().recordAt()).isEqualTo("2026-09-03 06:30");

        var restored = mutator.resolve(
            completed.documentJson(), completed.event().id(), EventDocumentMutator.Resolution.RESTORE,
            completed.event().updatedAt()
        );
        assertThat(restored.event().completed()).isFalse();
        assertThat(restored.event().completedAt()).isEmpty();
        assertThat(restored.event().recordAt()).isEqualTo("2026-09-01 09:00");
    }

    @Test
    void keepsPointEventsOnTheirOriginalTimeWhenCompleted() throws Exception {
        var created = mutator.create(document(), input("面试", "电话面试", "2026-09-05 09:00", ""));
        var completed = mutator.resolve(
            created.documentJson(), created.event().id(), EventDocumentMutator.Resolution.MISS,
            created.event().updatedAt()
        );

        assertThat(completed.event().completed()).isTrue();
        assertThat(completed.event().missed()).isTrue();
        assertThat(completed.event().completedAt()).isEmpty();
        assertThat(completed.event().recordAt()).isEqualTo("2026-09-05 09:00");
    }

    @Test
    void updatesKnownFieldsAndPreservesUnknownEventFields() throws Exception {
        String source = """
            {"applications":[{"id":"app-1","company":"Example","position":"Engineer"}],
            "events":[{"id":"evt-1","applicationId":"app-1","type":"面试","title":"一面",
            "startsAt":"2026-09-05 09:00","createdAt":"2026-09-01 08:00","providerId":"keep-me"}]}
            """;

        var updated = mutator.update(
            source, "evt-1", input("面试", "二面", "2026-09-06 10:00", ""), "2026-09-01 08:00"
        );
        JsonNode event = mapper.readTree(updated.documentJson()).path("events").get(0);

        assertThat(event.path("title").asText()).isEqualTo("二面");
        assertThat(event.path("providerId").asText()).isEqualTo("keep-me");
        assertThat(event.has("endsAt")).isFalse();
    }

    @Test
    void upgradesAnUnversionedLegacyEventOnItsFirstMutation() throws Exception {
        String source = """
            {"applications":[{"id":"app-1","company":"Example","position":"Engineer","stage":"笔试",
            "status":"等待结果","timeline":[]}],"events":[{"id":"evt-legacy","applicationId":"app-1",
            "type":"笔试","title":"旧版笔试","startsAt":"2026-09-05 09:00"}]}
            """;

        var completed = mutator.resolve(
            source, "evt-legacy", EventDocumentMutator.Resolution.COMPLETE, "__legacy_unversioned__"
        );

        assertThat(completed.event().completed()).isTrue();
        assertThat(completed.event().updatedAt()).isEqualTo("2026-09-03 06:30");
    }

    @Test
    void acceptsABlankVersionOnlyBeforeTheFirstLegacyMutation() throws Exception {
        String source = """
            {"applications":[{"id":"app-1","company":"Example","position":"Engineer","stage":"笔试",
            "status":"等待结果","timeline":[]}],"events":[{"id":"evt-legacy","applicationId":"app-1",
            "type":"笔试","title":"旧版笔试","startsAt":"2026-09-05 09:00","createdAt":"2026-09-04 08:00"}]}
            """;

        var completed = mutator.resolve(
            source, "evt-legacy", EventDocumentMutator.Resolution.COMPLETE, ""
        );
        assertThat(completed.event().updatedAt()).isEqualTo("2026-09-03 06:30");
        assertThrows(EventDocumentMutator.ValidationException.class, () ->
            mutator.resolve(completed.documentJson(), "evt-legacy", EventDocumentMutator.Resolution.RESTORE, "")
        );
    }

    @Test
    void deletesTimelineHistoryAndRollsProgressBackToTheLatestRemainingEvent() throws Exception {
        String source = """
            {"applications":[{"id":"app-1","company":"Example","position":"Engineer","stage":"面试",
            "status":"等待结果","timeline":[{"id":"t1","eventId":"evt-interview","title":"新增面试安排：一面"},
            {"id":"t2","eventId":"evt-test","title":"新增笔试安排：笔试"}]}],
            "events":[{"id":"evt-test","applicationId":"app-1","type":"笔试","title":"笔试",
            "startsAt":"2026-09-04 09:00","createdAt":"2026-09-01 08:00"},
            {"id":"evt-interview","applicationId":"app-1","type":"面试","title":"一面",
            "startsAt":"2026-09-05 09:00","createdAt":"2026-09-02 08:00"}],"settings":{"kept":true}}
            """;

        var deleted = mutator.delete(source, "evt-interview", "2026-09-02 08:00");
        JsonNode saved = mapper.readTree(deleted.documentJson());
        JsonNode application = saved.path("applications").get(0);

        assertThat(saved.path("events")).hasSize(1);
        assertThat(application.path("timeline")).hasSize(1);
        assertThat(application.path("stage").asText()).isEqualTo("笔试");
        assertThat(application.path("status").asText()).isEqualTo("等待结果");
        assertThat(saved.path("settings").path("kept").asBoolean()).isTrue();
    }

    private String document() {
        return """
            {"applications":[{"id":"app-1","company":"Example","position":"Engineer","stage":"已投递",
            "status":"等待结果","timeline":[],"futureField":7}],"events":[],"settings":{"theme":"dark"}}
            """;
    }

    private EventDocumentMutator.EventInput input(String type, String title, String startsAt, String endsAt) {
        return new EventDocumentMutator.EventInput(
            "app-1", type, title, startsAt, endsAt, "线上", "准备材料"
        );
    }
}
