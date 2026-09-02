package com.jobtracker.migrationpoc.database;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyDatabaseUrlTest {
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
