package com.jobtracker.migrationpoc.database;

import com.jobtracker.migrationpoc.application.ApplicationDocumentMutator;
import com.jobtracker.migrationpoc.compat.LegacyPasswordVerifier;
import com.jobtracker.migrationpoc.compat.LegacySecretCrypto;
import com.jobtracker.migrationpoc.compat.LegacySecretCryptoWriter;
import com.jobtracker.migrationpoc.config.DatabaseSchemaInitializer;
import com.jobtracker.migrationpoc.security.PocPersistentSessionStore;
import com.jobtracker.migrationpoc.backup.BackupDocumentValidator;
import com.jobtracker.migrationpoc.event.EventDocumentMutator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteCompatibilityTest {
    @Test
    void initializesRegistersAndPersistsSessions() throws Exception {
        Path directory = Path.of("target", "sqlite-tests").toAbsolutePath();
        Files.createDirectories(directory);
        Path database = directory.resolve(UUID.randomUUID() + ".db");
        MockEnvironment environment = new MockEnvironment()
            .withProperty("APP_DATABASE_URL", "jdbc:sqlite:" + database)
            .withProperty("ALLOW_REGISTRATION", "true")
            .withProperty("PERSISTENT_SESSION_ENABLED", "true")
            .withProperty("ADMIN_EMAIL", "admin@example.com");

        new DatabaseSchemaInitializer(environment).run(null);
        ObjectMapper mapper = new ObjectMapper();
        ApplicationSandboxService applications = new ApplicationSandboxService(
            environment, mapper, new ApplicationDocumentMutator(mapper)
        );
        LegacyPasswordVerifier passwords = new LegacyPasswordVerifier();
        AccountSandboxService accounts = new AccountSandboxService(environment, applications, passwords);
        var registered = accounts.register("admin@example.com", "correct-horse-battery", "");
        assertThat(registered.displayName()).isEqualTo("admin");
        assertThat(accounts.findByEmail("admin@example.com")).isPresent();

        AdminSandboxService admin = new AdminSandboxService(environment, mapper, applications, passwords);
        assertThat(admin.overview("admin@example.com").users()).hasSize(1);
        admin.setRegistration("admin@example.com", false);
        assertThat(accounts.registrationOpen()).isFalse();
        admin.setRegistration("admin@example.com", true);
        assertThat(accounts.registrationOpen()).isTrue();
        admin.setRegistrationCode("admin@example.com", "invite-2026", false);
        assertThat(admin.overview("admin@example.com").summary().registrationCodeEnabled()).isTrue();
        var group = admin.createGroup("admin@example.com", "第一面试室");
        admin.assignGroup("admin@example.com", registered.id(), Long.parseLong(group.groupId()));
        assertThat(admin.overview("admin@example.com").groups()).hasSize(1);
        admin.assignGroup("admin@example.com", registered.id(), null);
        admin.deleteGroup("admin@example.com", Long.parseLong(group.groupId()));

        LegacySecretCrypto secretCrypto = new LegacySecretCrypto();
        MailInboxService mail = new MailInboxService(
            environment, secretCrypto, new LegacySecretCryptoWriter(secretCrypto)
        );
        assertThat(mail.inbox("admin@example.com").messages()).isEmpty();

        BackupSandboxService backups = new BackupSandboxService(
            environment, applications, new BackupDocumentValidator(mapper), mapper
        );
        var links = mapper.createArrayNode();
        links.addObject().put("company", "Example").put("url", "https://example.com");
        assertThat(backups.saveCompanyLinks("admin@example.com", links).items()).hasSize(1);
        backups.importDocument(
            "admin@example.com",
            "{\"applications\":[{\"id\":\"a1\",\"company\":\"Example\"}],\"events\":[],\"settings\":{}}",
            "sqlite-test"
        );
        assertThat(backups.backups("admin@example.com").items()).isNotEmpty();
        assertThat(applications.findApplications("admin@example.com").total()).isEqualTo(1);
        EventSandboxService events = new EventSandboxService(
            environment, applications, new EventDocumentMutator(mapper)
        );
        events.create("admin@example.com", new EventDocumentMutator.EventInput(
            "a1", "面试", "一面", "2026-10-08 14:00", "", "线上", "准备材料"
        ));
        assertThat(events.findEvents("admin@example.com").total()).isEqualTo(1);

        PocPersistentSessionStore sessions = new PocPersistentSessionStore(environment, applications);
        String token = sessions.issue("admin@example.com");
        assertThat(sessions.verifyEmail(token)).contains("admin@example.com");
        sessions.revoke(token);
        assertThat(sessions.verifyEmail(token)).isEmpty();
    }
}
