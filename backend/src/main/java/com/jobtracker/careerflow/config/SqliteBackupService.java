package com.jobtracker.careerflow.config;

import com.jobtracker.careerflow.database.LegacyDatabaseUrl;
import com.jobtracker.careerflow.database.PooledConnections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Properties;

@Component
public class SqliteBackupService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SqliteBackupService.class);
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final Environment environment;

    public SqliteBackupService(Environment environment) {
        this.environment = environment;
    }

    @Scheduled(cron = "${SQLITE_BACKUP_CRON:0 30 3 * * *}", zone = "Asia/Shanghai")
    public void scheduledBackup() {
        if (!AppEnvironment.flag(environment, true, "SQLITE_BACKUP_ENABLED")) return;
        try {
            backupNow();
        } catch (Exception exception) {
            LOGGER.error("SQLite automatic backup failed", exception);
        }
    }

    Path backupNow() throws Exception {
        LegacyDatabaseUrl database = LegacyDatabaseUrl.parse(AppEnvironment.databaseUrl(environment));
        if (!database.isSqlite()) return null;
        Path databaseFile = sqlitePath(database);
        if (databaseFile == null || !Files.exists(databaseFile)) return null;
        Path backupDirectory = backupDirectory(databaseFile);
        Files.createDirectories(backupDirectory);
        Path backup = backupDirectory.resolve("jobtracker-" + LocalDateTime.now().format(FILE_TIME) + ".db");
        Properties properties = new Properties();
        properties.setProperty("ApplicationName", "job-tracker-sqlite-backup");
        try (var connection = PooledConnections.open(database, properties);
             var statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(PASSIVE)");
            statement.execute("VACUUM INTO '" + backup.toString().replace("'", "''") + "'");
        }
        prune(backupDirectory, AppEnvironment.sqliteBackupRetention(environment));
        LOGGER.info("SQLite backup created: {}", backup);
        return backup;
    }

    private Path sqlitePath(LegacyDatabaseUrl database) {
        String raw = database.jdbcUrl().substring("jdbc:sqlite:".length());
        if (raw.isBlank() || raw.equals(":memory:") || raw.startsWith("file::memory:")) return null;
        int query = raw.indexOf('?');
        if (query >= 0) raw = raw.substring(0, query);
        return Path.of(raw).toAbsolutePath().normalize();
    }

    private Path backupDirectory(Path databaseFile) {
        String configured = environment.getProperty("SQLITE_BACKUP_DIR", "").trim();
        if (!configured.isBlank()) return Path.of(configured).toAbsolutePath().normalize();
        Path dataDirectory = databaseFile.getParent();
        Path appDirectory = dataDirectory == null ? null : dataDirectory.getParent();
        return (appDirectory == null ? Path.of("backups") : appDirectory.resolve("backups"))
            .toAbsolutePath().normalize();
    }

    private void prune(Path directory, int retention) throws Exception {
        try (var files = Files.list(directory)) {
            var backups = files
                .filter(path -> path.getFileName().toString().matches("jobtracker-\\d{8}-\\d{6}\\.db"))
                .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                .toList();
            for (int index = retention; index < backups.size(); index++) Files.deleteIfExists(backups.get(index));
        }
    }
}
