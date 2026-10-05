package com.jobtracker.careerflow.database;

import com.jobtracker.careerflow.application.ApplicationDocumentMutator;
import com.jobtracker.careerflow.compat.LegacyPasswordVerifier;
import com.jobtracker.careerflow.compat.LegacySecretCrypto;
import com.jobtracker.careerflow.compat.LegacySecretCryptoWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AdminServiceTest {
    @Test
    void enablesAdminByDefaultWhenTheBusinessDatabaseIsConfigured() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("APP_DATABASE_URL", "postgres://db.example.com/main");
        var status = service(environment).status();
        assertThat(status.enabled()).isTrue();
        assertThat(status.requested()).isTrue();
        assertThat(status.sandboxEnabled()).isTrue();
    }

    @Test
    void canBeExplicitlyDisabled() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("APP_DATABASE_URL", "postgres://db.example.com/main")
            .withProperty("ADMIN_ENABLED", "false");
        assertThat(service(environment).status().enabled()).isFalse();
    }

    @Test
    void remainsDisabledUntilTheDatabaseIsConfigured() {
        var status = service(new MockEnvironment()).status();
        assertThat(status.enabled()).isFalse();
        assertThat(status.message()).contains("数据库");
    }

    @Test
    void doesNotGrantAdminAccessWhenAdminFeaturesAreDisabled() throws Exception {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("APP_DATABASE_URL", "postgres://db.example.com/main")
            .withProperty("ADMIN_ENABLED", "false")
            .withProperty("ADMIN_EMAIL", "admin@example.com");
        assertThat(service(environment).isAdmin("admin@example.com")).isFalse();
    }

    @Test
    void mapsApplicationTimelineAndEventsWithoutReturningSecrets() throws Exception {
        AdminService service = service(new MockEnvironment());
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
            .extracting(AdminService.FlowStep::title)
            .containsExactly("已投递", "进入筛选", "面试 · 一面 · 通过");
        assertThat(details.toString()).doesNotContain("must-not-be-returned");
    }

    @Test
    void reportsResumeConfiguredOnlyWhenAnEntryContainsInformation() {
        AdminService service = service(new MockEnvironment());
        assertThat(service.hasConfiguredResume("{\"settings\":{\"interviewWorkbench\":{\"resume\":{\"education\":[],\"internships\":[],\"projects\":[]}}}}"))
            .isFalse();
        assertThat(service.hasConfiguredResume("{\"settings\":{\"interviewWorkbench\":{\"resume\":{\"education\":[{\"school\":\"\",\"major\":\"\"}],\"internships\":[],\"projects\":[]}}}}"))
            .isFalse();
        assertThat(service.hasConfiguredResume("{\"settings\":{\"interviewWorkbench\":{\"resume\":{\"education\":[{\"school\":\"Example University\"}]}}}}"))
            .isTrue();
    }

    private AdminService service(MockEnvironment environment) {
        ObjectMapper mapper = new ObjectMapper();
        ApplicationService sandbox = new ApplicationService(
            environment, mapper, new ApplicationDocumentMutator(mapper)
        );
        LegacySecretCrypto crypto = new LegacySecretCrypto();
        return new AdminService(environment, mapper, sandbox, new LegacyPasswordVerifier(), crypto,
            new LegacySecretCryptoWriter(crypto));
    }
}
