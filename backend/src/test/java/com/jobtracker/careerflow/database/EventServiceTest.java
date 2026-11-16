package com.jobtracker.careerflow.database;

import com.jobtracker.careerflow.event.EventDocumentMutator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class EventServiceTest {
    @Test
    void sharedTimelineShowsCurrentCompanyForSavedEvents() throws Exception {
        var mutator = new EventDocumentMutator(new ObjectMapper());
        var page = mutator.page("""
            {
              "applications": [{"id":"app-1","company":"新公司名"}],
              "events": [
                {"id":"linked","applicationId":"app-1","company":"旧公司名","startsAt":"2026-09-21 10:00"},
                {"id":"unlinked","company":"历史公司名","startsAt":"2026-09-21 11:00"}
              ]
            }
            """, 100);

        assertThat(EventService.sharedEvents(page))
            .extracting(EventService.SharedEvent::id, EventService.SharedEvent::company)
            .containsExactly(tuple("linked", "新公司名"), tuple("unlinked", "历史公司名"));
    }

    @Test
    void sharedTimelineIncludesOnlyPointEvents() {
        assertThat(EventService.isPointEvent("2026-09-21 10:00", "")).isTrue();
        assertThat(EventService.isPointEvent("2026-09-21 10:00", null)).isTrue();
        assertThat(EventService.isPointEvent("2026-09-21 10:00", "2026-09-21 10:00")).isTrue();
        assertThat(EventService.isPointEvent("2026-09-21 10:00", "2026-09-21 11:00")).isFalse();
    }
}
