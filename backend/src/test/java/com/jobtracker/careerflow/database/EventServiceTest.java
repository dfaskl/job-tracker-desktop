package com.jobtracker.careerflow.database;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventServiceTest {
    @Test
    void sharedTimelineIncludesOnlyPointEvents() {
        assertThat(EventService.isPointEvent("2026-09-21 10:00", "")).isTrue();
        assertThat(EventService.isPointEvent("2026-09-21 10:00", null)).isTrue();
        assertThat(EventService.isPointEvent("2026-09-21 10:00", "2026-09-21 10:00")).isTrue();
        assertThat(EventService.isPointEvent("2026-09-21 10:00", "2026-09-21 11:00")).isFalse();
    }
}
