package com.jobtracker.careerflow.database;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyDatabaseUrlTest {
    @Test
    void parsesSqlitePaths() {
        LegacyDatabaseUrl parsed = LegacyDatabaseUrl.parse("sqlite:/srv/jobtracker/data/jobtracker.db");
        assertThat(parsed.jdbcUrl()).isEqualTo("jdbc:sqlite:/srv/jobtracker/data/jobtracker.db");
        assertThat(parsed.isSqlite()).isTrue();
        assertThat(parsed.username()).isNull();
    }
    @Test
    void convertsNodeStylePostgresUrlWithoutLoggingCredentials() {
        LegacyDatabaseUrl parsed = LegacyDatabaseUrl.parse(
            "postgresql://user%40example.com:p%40ss@db.example.com:5433/jobs?sslmode=require"
        );

        assertThat(parsed.jdbcUrl()).isEqualTo("jdbc:postgresql://db.example.com:5433/jobs?sslmode=require");
        assertThat(parsed.username()).isEqualTo("user@example.com");
        assertThat(parsed.password()).isEqualTo("p@ss");
    }

    @Test
    void keepsJdbcUrlAsIs() {
        LegacyDatabaseUrl parsed = LegacyDatabaseUrl.parse("jdbc:postgresql://localhost/jobs");
        assertThat(parsed.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost/jobs");
        assertThat(parsed.username()).isNull();
    }
}
