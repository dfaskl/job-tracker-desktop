package com.jobtracker.migrationpoc.database;

import com.jobtracker.migrationpoc.application.ApplicationDocumentMutator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationSandboxServiceTest {
    @Test
    void remainsDisabledWithoutExplicitOptIn() {
        var status = service(new MockEnvironment()
            .withProperty("DATABASE_URL", "postgres://user:pass@prod.example.com/main")
            .withProperty("POC_WRITE_DATABASE_URL", "postgres://user:pass@test.example.com/test")
        ).status();

        assertThat(status.enabled()).isFalse();
        assertThat(status.message()).contains("未开启");
    }

    @Test
    void refusesTheProductionDatabaseEvenWhenQueryParametersDiffer() {
        var status = service(new MockEnvironment()
            .withProperty("POC_WRITE_ENABLED", "true")
            .withProperty("DATABASE_URL", "postgres://user:pass@prod.example.com/main?sslmode=require")
            .withProperty("POC_WRITE_DATABASE_URL", "postgres://other:pass@prod.example.com/main?connectTimeout=10")
        ).status();

        assertThat(status.enabled()).isFalse();
        assertThat(status.isolated()).isFalse();
        assertThat(status.message()).contains("相同");
    }

    @Test
    void enablesOnlyAnExplicitlyConfiguredDifferentDatabase() {
        var status = service(new MockEnvironment()
            .withProperty("POC_WRITE_ENABLED", "true")
            .withProperty("DATABASE_URL", "postgres://user:pass@prod.example.com/main")
            .withProperty("POC_WRITE_DATABASE_URL", "postgres://user:pass@test.example.com/test")
        ).status();

        assertThat(status.enabled()).isTrue();
        assertThat(status.configured()).isTrue();
        assertThat(status.isolated()).isTrue();
    }

    private ApplicationSandboxService service(MockEnvironment environment) {
        ObjectMapper mapper = new ObjectMapper();
        return new ApplicationSandboxService(environment, mapper, new ApplicationDocumentMutator(mapper));
    }
}
