package com.jobtracker.migrationpoc.database;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleAdvicePlannerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ScheduleAdvicePlanner planner = new ScheduleAdvicePlanner(mapper);

    @Test
    void acceptsLegacyTimestampsWithSeconds() throws Exception {
        var schedules = mapper.readTree("""
            [{"id":"a","company":"米哈游","title":"笔试","startsAt":"2026-09-06 10:00:00","endsAt":"2026-09-06 10:00:00"},
             {"id":"b","company":"其他公司","title":"面试","startsAt":"2026-09-06 14:00","endsAt":"2026-09-06 14:00"}]
            """);
        var result = planner.plan(schedules, LocalDateTime.of(2026, 9, 5, 12, 0));
        assertThat(result.path("plans").toString()).contains("米哈游").contains("2026-09-06 10:00-11:30");
    }
    @Test
    void shortensFixedPointToSixtyMinutesWithWarning() throws Exception {
        var schedules = mapper.readTree("""
            [{"id":"a","company":"甲","title":"笔试","startsAt":"2026-09-06 19:00","endsAt":"2026-09-06 19:00"},
             {"id":"b","company":"乙","title":"面试","startsAt":"2026-09-06 20:00","endsAt":"2026-09-06 20:00"}]
            """);
        var result = planner.plan(schedules, LocalDateTime.of(2026, 9, 6, 10, 0));
        assertThat(result.path("plans").get(0).asText()).contains("19:00-20:00");
        assertThat(result.path("warnings").get(0).asText()).contains("60 分钟");
        assertThat(result.path("timeline").get(0).path("status").asText()).isEqualTo("tight");
        assertThat(result.path("conflicts").isEmpty()).isTrue();
    }

    @Test
    void reportsConflictWhenFixedPointHasLessThanSixtyMinutes() throws Exception {
        var schedules = mapper.readTree("""
            [{"id":"a","company":"甲","title":"笔试","startsAt":"2026-09-06 19:00","endsAt":"2026-09-06 19:00"},
             {"id":"b","company":"乙","title":"面试","startsAt":"2026-09-06 19:45","endsAt":"2026-09-06 19:45"}]
            """);
        var result = planner.plan(schedules, LocalDateTime.of(2026, 9, 6, 10, 0));
        assertThat(result.path("conflicts").get(0).asText()).contains("不足 60 分钟");
        assertThat(result.path("timeline").get(0).path("status").asText()).isEqualTo("conflict");
    }

    @Test
    void usesShortFlexibleGapBeforeDeclaringConflict() throws Exception {
        var schedules = mapper.readTree("""
            [{"id":"a","company":"固定","title":"面试","startsAt":"2026-09-06 11:15","endsAt":"2026-09-06 11:15"},
             {"id":"b","company":"弹性","title":"笔试","startsAt":"2026-09-06 10:00","endsAt":"2026-09-06 11:15"}]
            """);
        var result = planner.plan(schedules, LocalDateTime.of(2026, 9, 6, 10, 0));
        assertThat(result.path("plans").toString()).contains("10:00-11:15");
        assertThat(result.path("warnings").toString()).contains("75 分钟");
        assertThat(result.path("timeline").toString()).contains("\"status\":\"tight\"");
    }

    @Test
    void exposesStartTimeRangeEndingNinetyMinutesBeforeNextSchedule() throws Exception {
        var schedules = mapper.readTree("""
            [{"id":"a","company":"自由安排","title":"笔试","startsAt":"2026-09-06 10:00","endsAt":"2026-09-06 18:00"},
             {"id":"b","company":"窗口内安排","title":"面试","startsAt":"2026-09-06 14:00","endsAt":"2026-09-06 14:00"}]
            """);
        var result = planner.plan(schedules, LocalDateTime.of(2026, 9, 5, 12, 0));
        assertThat(result.path("timeline").get(0).path("showWindow").asBoolean()).isTrue();
        assertThat(result.path("timeline").get(0).path("windowStart").asText()).isEqualTo("2026-09-06 10:00");
        assertThat(result.path("timeline").get(0).path("windowEnd").asText()).isEqualTo("2026-09-06 12:30");
    }
}
