package com.jobtracker.migrationpoc.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteBackupServiceTest {
    @Test
    void createsAConsistentStandaloneBackup() throws Exception {
        Path root = Path.of("target", "sqlite-backup-tests", UUID.randomUUID().toString()).toAbsolutePath();
        Files.createDirectories(root.resolve("data"));
        Path database = root.resolve("data/jobtracker.db");
        MockEnvironment environment = new MockEnvironment()
            .withProperty("APP_DATABASE_URL", "jdbc:sqlite:" + database)
            .withProperty("ADMIN_EMAIL", "admin@example.com");
        new DatabaseSchemaInitializer(environment).run(null);

        Path backup = new SqliteBackupService(environment).backupNow();

        assertThat(backup).exists().isRegularFile();
        assertThat(backup.getParent()).isEqualTo(root.resolve("backups"));
        assertThat(Files.size(backup)).isGreaterThan(0);
    }
}
