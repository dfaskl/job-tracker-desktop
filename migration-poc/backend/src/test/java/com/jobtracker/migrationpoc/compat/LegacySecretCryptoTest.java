package com.jobtracker.migrationpoc.compat;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class LegacySecretCryptoTest {
    private final LegacySecretCrypto crypto = new LegacySecretCrypto();

    @Test
    void decryptsCiphertextGeneratedByNodeAes256Gcm() {
        String plaintext = crypto.decrypt(
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            hex("cd294fbc6991d662f096d93e6ecc4472943441e135f2"),
            hex("00112233445566778899aabb"),
            hex("a47400551c1fa7ac48800b29313b79a4")
        );

        assertThat(plaintext).isEqualTo("poc-api-key-not-secret");
    }

    private byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }
}
