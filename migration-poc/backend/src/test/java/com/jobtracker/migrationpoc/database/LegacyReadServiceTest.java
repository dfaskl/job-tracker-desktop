package com.jobtracker.migrationpoc.database;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyReadServiceTest {
    private final LegacyReadService service = new LegacyReadService(new MockEnvironment(), new ObjectMapper());

    @Test
    void mapsOnlyListFieldsAndSortsByLatestUpdate() throws Exception {
        String json = """
            {"applications":[
              {"id":"old","company":"Alpha","position":"Engineer","notes":"private","updatedAt":"2026-01-01 08:00"},
              {"id":"new","company":"Beta","position":"Analyst","city":"Shanghai","stage":"Interview","updatedAt":"2026-02-01 08:00"}
            ],"events":[]}
            """;

        LegacyReadService.ApplicationPage page = service.mapApplications(json);

        assertEquals(2, page.total());
        assertFalse(page.truncated());
        assertEquals("new", page.applications().getFirst().id());
        assertEquals("Shanghai", page.applications().getFirst().city());
    }

    @Test
    void rejectsAnIncompatibleBusinessDocument() {
        assertThrows(IllegalStateException.class, () -> service.mapApplications("{\"applications\":{}}"));
    }
}
