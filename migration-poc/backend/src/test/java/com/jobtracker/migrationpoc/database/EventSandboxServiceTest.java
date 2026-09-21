package com.jobtracker.migrationpoc.database;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventSandboxServiceTest {
    @Test
    void sharedTimelineIncludesOnlyPointEvents() {
        assertThat(EventSandboxService.isPointEvent("2026-09-21 10:00", "")).isTrue();
        assertThat(EventSandboxService.isPointEvent("2026-09-21 10:00", null)).isTrue();
        assertThat(EventSandboxService.isPointEvent("2026-09-21 10:00", "2026-09-21 10:00")).isTrue();
        assertThat(EventSandboxService.isPointEvent("2026-09-21 10:00", "2026-09-21 11:00")).isFalse();
    }
}
