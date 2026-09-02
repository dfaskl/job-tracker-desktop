package com.jobtracker.migrationpoc.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PocSessionManagerTest {
    @Test
    void issuesAndVerifiesSignedSessions() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("POC_SESSION_SECRET", "0123456789abcdef0123456789abcdef");
        PocSessionManager manager = new PocSessionManager(environment);

        String token = manager.issue(42);

        assertEquals(42, manager.verify(token).orElseThrow().userId());
        assertFalse(manager.verify(token + "x").isPresent());
    }

    @Test
    void requiresAStrongServerSecret() {
        PocSessionManager manager = new PocSessionManager(
            new MockEnvironment().withProperty("POC_SESSION_SECRET", "short")
        );

        assertFalse(manager.isConfigured());
        assertTrue(manager.verify("anything").isEmpty());
    }
}
