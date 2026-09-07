package com.jobtracker.migrationpoc.database;

import com.jobtracker.migrationpoc.application.ApplicationDocumentMutator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationSandboxServiceTest {
    @Test
    void remainsDisabledWithoutADatabase() {
        var status = service(new MockEnvironment()).status();
        assertThat(status.enabled()).isFalse();
        assertThat(status.configured()).isFalse();
        assertThat(status.message()).contains("尚未配置");
    }

    @Test
    void enablesTheFormalSingleDatabaseConfiguration() {
        var status = service(new MockEnvironment()
            .withProperty("APP_DATABASE_URL", "postgres://user:pass@db.example.com/jobtracker")
        ).status();
        assertThat(status.enabled()).isTrue();
        assertThat(status.configured()).isTrue();
        assertThat(status.message()).contains("已连接");
    }

    @Test
    void keepsExistingRenderDatabaseVariablesCompatible() {
        var status = service(new MockEnvironment()
            .withProperty("DATABASE_URL", "postgres://user:pass@prod.example.com/main")
            .withProperty("POC_WRITE_DATABASE_URL", "postgres://user:pass@test.example.com/test")
        ).status();
        assertThat(status.enabled()).isTrue();
    }

    @Test
    void rejectsMalformedDatabaseAddresses() {
        var status = service(new MockEnvironment().withProperty("APP_DATABASE_URL", "not-a-database" )).status();
        assertThat(status.enabled()).isFalse();
        assertThat(status.configured()).isTrue();
        assertThat(status.message()).contains("格式无效");
    }

    private ApplicationSandboxService service(MockEnvironment environment) {
        ObjectMapper mapper = new ObjectMapper();
        return new ApplicationSandboxService(environment, mapper, new ApplicationDocumentMutator(mapper));
    }
}