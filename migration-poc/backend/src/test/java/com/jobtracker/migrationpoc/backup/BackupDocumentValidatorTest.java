package com.jobtracker.migrationpoc.backup;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BackupDocumentValidatorTest {
    private final BackupDocumentValidator validator = new BackupDocumentValidator(new ObjectMapper());

    @Test
    void acceptsTheLegacyBusinessDocumentAndCountsRecords() throws Exception {
        var summary = validator.validate("""
            {"applications":[{"id":"a1"},{"id":"a2"}],"events":[{"id":"e1"}],"settings":{"theme":"dark"}}
            """);

        assertThat(summary.applicationCount()).isEqualTo(2);
        assertThat(summary.eventCount()).isEqualTo(1);
    }

    @Test
    void rejectsMalformedOrStructurallyIncompatibleBackups() {
        assertThrows(Exception.class, () -> validator.validate("not-json"));
        assertThrows(BackupDocumentValidator.InvalidBackupException.class, () ->
            validator.validate("{\"applications\":{},\"events\":[]}")
        );
    }
}
