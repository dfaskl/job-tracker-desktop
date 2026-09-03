package com.jobtracker.migrationpoc.database;

import com.jobtracker.migrationpoc.application.ApplicationDocumentMutator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AdminSandboxServiceTest {
    @Test
    void remainsDisabledUnlessBothAdminAndIsolatedWriteFlagsAreEnabled() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("POC_WRITE_ENABLED", "true")
            .withProperty("DATABASE_URL", "postgres://prod.example.com/main")
            .withProperty("POC_WRITE_DATABASE_URL", "postgres://test.example.com/test");

        var status = service(environment).status();

        assertThat(status.enabled()).isFalse();
        assertThat(status.requested()).isFalse();
        assertThat(status.sandboxEnabled()).isTrue();
    }

    @Test
    void refusesAdminModeWhenTheWriteDatabaseIsProduction() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("POC_ADMIN_ENABLED", "true")
            .withProperty("POC_WRITE_ENABLED", "true")
            .withProperty("DATABASE_URL", "postgres://same.example.com/main")
            .withProperty("POC_WRITE_DATABASE_URL", "postgres://same.example.com/main?sslmode=require");

        var status = service(environment).status();

        assertThat(status.enabled()).isFalse();
        assertThat(status.message()).contains("相同");
    }

    @Test
    void enablesAdminOnlyForAnExplicitIndependentSandbox() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("POC_ADMIN_ENABLED", "true")
            .withProperty("POC_WRITE_ENABLED", "true")
            .withProperty("DATABASE_URL", "postgres://prod.example.com/main")
            .withProperty("POC_WRITE_DATABASE_URL", "postgres://test.example.com/test");

        assertThat(service(environment).status().enabled()).isTrue();
    }

    @Test
    void mapsApplicationTimelineAndEventsWithoutReturningSecrets() throws Exception {
        AdminSandboxService service = service(new MockEnvironment());
        String document = """
            {
              "applications":[{
                "id":"app-1","company":"Example","position":"Engineer","stage":"面试","status":"等待结果",
                "appliedDate":"2026-09-01","timeline":[{"at":"2026-09-02","title":"进入筛选"}],
                "password":"must-not-be-returned"
              }],
              "events":[{
                "applicationId":"app-1","type":"面试","title":"一面","startsAt":"2026-09-03 10:00","completed":true,"result":"通过",
                "apiKey":"must-not-be-returned"
              }]
            }
            """;

        var details = service.mapDetails(8, "person@example.com", document);

        assertThat(details.totalApplications()).isEqualTo(1);
        assertThat(details.applications().getFirst().flow())
            .extracting(AdminSandboxService.FlowStep::title)
            .containsExactly("已投递", "进入筛选", "面试 · 一面 · 通过");
        assertThat(details.toString()).doesNotContain("must-not-be-returned");
    }

    private AdminSandboxService service(MockEnvironment environment) {
        ObjectMapper mapper = new ObjectMapper();
        ApplicationSandboxService sandbox = new ApplicationSandboxService(
            environment, mapper, new ApplicationDocumentMutator(mapper)
        );
        return new AdminSandboxService(environment, mapper, sandbox);
    }
}
