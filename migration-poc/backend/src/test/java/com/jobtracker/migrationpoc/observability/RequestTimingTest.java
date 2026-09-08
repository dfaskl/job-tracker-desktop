package com.jobtracker.migrationpoc.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestTimingTest {
    @Test
    void formatsSegmentedServerTimingWithoutSensitiveDetails() {
        RequestTiming.begin();
        try {
            RequestTiming.record("db", 2_500_000);
            RequestTiming.record("sql", 3_000_000);
            RequestTiming.record("sql", 4_000_000);
            RequestTiming.record("ai", 5_500_000);

            String header = RequestTiming.serverTiming();

            assertThat(header)
                .contains("db;dur=2.50;desc=\"1 call\"")
                .contains("sql;dur=7.00;desc=\"2 calls\"")
                .contains("ai;dur=5.50;desc=\"1 call\"")
                .contains("app;dur=")
                .contains("total;dur=")
                .doesNotContain("SELECT", "password", "email");
        } finally {
            RequestTiming.clear();
        }
    }
}