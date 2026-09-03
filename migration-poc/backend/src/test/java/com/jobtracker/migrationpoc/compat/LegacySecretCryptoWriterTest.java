package com.jobtracker.migrationpoc.compat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacySecretCryptoWriterTest {
    @Test
    void writesCiphertextThatTheLegacyCompatibleReaderCanDecrypt() {
        LegacySecretCrypto reader = new LegacySecretCrypto();
        LegacySecretCryptoWriter writer = new LegacySecretCryptoWriter(reader);
        String encryptionKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

        var encrypted = writer.encrypt(encryptionKey, "test-api-key-value");
        String plaintext = reader.decrypt(
            encryptionKey, encrypted.encrypted(), encrypted.iv(), encrypted.authTag()
        );

        assertThat(plaintext).isEqualTo("test-api-key-value");
        assertThat(encrypted.iv()).hasSize(12);
        assertThat(encrypted.authTag()).hasSize(16);
    }
}
