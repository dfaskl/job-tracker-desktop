package com.jobtracker.migrationpoc.compat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyPasswordVerifierTest {
    @org.junit.jupiter.api.Test
    void createsAHashCompatibleWithLegacyLogin() {
        LegacyPasswordVerifier verifier = new LegacyPasswordVerifier();
        var record = verifier.create("a-secure-test-password");
        org.assertj.core.api.Assertions.assertThat(verifier.verify("a-secure-test-password", record.salt(), record.hash())).isTrue();
        org.assertj.core.api.Assertions.assertThat(verifier.verify("wrong-password", record.salt(), record.hash())).isFalse();
    }

    private final LegacyPasswordVerifier verifier = new LegacyPasswordVerifier();

    @Test
    void verifiesHashGeneratedByNodeCryptoScrypt() {
        String hash = "90f58584d0d9caefd484464d32143aed9f18b8a847d54ada8e0f2137b0980406"
            + "d7884c61070d513109e4ccb64cf432974cd0ccb27c850c1f7e3b610218cafb78";

        assertThat(verifier.verify("migration-poc-password", "00112233445566778899aabbccddeeff", hash)).isTrue();
        assertThat(verifier.verify("wrong-password", "00112233445566778899aabbccddeeff", hash)).isFalse();
    }
}
