package com.jobtracker.migrationpoc.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class AppEnvironmentTest {
    @Test
    void formalSettingsTakePriorityOverLegacyAliases() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("APP_DATABASE_URL", "postgres://formal/db")
            .withProperty("POC_WRITE_DATABASE_URL", "postgres://legacy/db")
            .withProperty("SESSION_SECRET", "formal-secret")
            .withProperty("POC_SESSION_SECRET", "legacy-secret");
        assertThat(AppEnvironment.databaseUrl(environment)).isEqualTo("postgres://formal/db");
        assertThat(AppEnvironment.sessionSecret(environment)).isEqualTo("formal-secret");
    }

    @Test
    void legacyRenderSettingsRemainSupported() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("DATABASE_URL", "postgres://render/db")
            .withProperty("POC_ENCRYPTION_KEY", "legacy-key");
        assertThat(AppEnvironment.databaseUrl(environment)).isEqualTo("postgres://render/db");
        assertThat(AppEnvironment.encryptionKey(environment)).isEqualTo("legacy-key");
    }

    @Test
    void productionFeaturesHaveUsefulDefaults() {
        MockEnvironment environment = new MockEnvironment();
        assertThat(AppEnvironment.persistentSessionsEnabled(environment)).isTrue();
        assertThat(AppEnvironment.aiCallsEnabled(environment)).isTrue();
        assertThat(AppEnvironment.adminEnabled(environment)).isTrue();
        assertThat(AppEnvironment.sessionDays(environment)).isEqualTo(7);
    }
}