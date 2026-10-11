package com.jobtracker.careerflow.database;

import com.jobtracker.careerflow.application.ApplicationDocumentMutator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationServiceTest {
    @Test
    void remainsDisabledWithoutADatabase() {
        var status = service(new MockEnvironment()).status();
        assertThat(status.enabled()).isFalse();
        assertThat(status.configured()).isFalse();
        assertThat(status.message()).contains("尚未配置");
    }

    @Test
    void enablesTheFormalSingleDatabaseConfiguration() throws Exception {
        var status = service(new MockEnvironment()
            .withProperty("APP_DATABASE_URL", "jdbc:sqlite:" + databasePath("formal"))
        ).status();
        assertThat(status.enabled()).isTrue();
        assertThat(status.configured()).isTrue();
        assertThat(status.message()).contains("已连接");
    }

    @Test
    void keepsExistingRenderDatabaseVariablesCompatible() throws Exception {
        var status = service(new MockEnvironment()
            .withProperty("DATABASE_URL", "jdbc:sqlite:" + databasePath("prod"))
            .withProperty("POC_WRITE_DATABASE_URL", "jdbc:sqlite:" + databasePath("test"))
        ).status();
        assertThat(status.enabled()).isTrue();
    }

    @Test
    void reportsDatabaseConnectionFailures() throws Exception {
        Path directory = Path.of("target", "sqlite-tests", UUID.randomUUID().toString()).toAbsolutePath();
        Files.createDirectories(directory);
        var status = service(new MockEnvironment()
            .withProperty("APP_DATABASE_URL", "jdbc:sqlite:" + directory)
        ).status();
        assertThat(status.enabled()).isFalse();
        assertThat(status.configured()).isTrue();
        assertThat(status.message()).contains("连接失败");
    }

    private Path databasePath(String name) throws Exception {
        Path directory = Path.of("target", "sqlite-tests").toAbsolutePath();
        Files.createDirectories(directory);
        return directory.resolve(name + "-" + UUID.randomUUID() + ".db");
    }

    @Test
    void rejectsMalformedDatabaseAddresses() {
        var status = service(new MockEnvironment().withProperty("APP_DATABASE_URL", "not-a-database" )).status();
        assertThat(status.enabled()).isFalse();
        assertThat(status.configured()).isTrue();
        assertThat(status.message()).contains("格式无效");
    }

    private ApplicationService service(MockEnvironment environment) {
        ObjectMapper mapper = new ObjectMapper();
        return new ApplicationService(environment, mapper, new ApplicationDocumentMutator(mapper));
    }
}
