package com.jobtracker.migrationpoc.compat;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

@Component
public class LegacySecretCryptoWriter {
    private static final int IV_BYTES = 12;
    private static final int TAG_BYTES = 16;

    private final LegacySecretCrypto legacySecretCrypto;
    private final SecureRandom secureRandom = new SecureRandom();

    public LegacySecretCryptoWriter(LegacySecretCrypto legacySecretCrypto) {
        this.legacySecretCrypto = legacySecretCrypto;
    }

    public EncryptedSecret encrypt(String configuredKey, String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(legacySecretCrypto.parseKey(configuredKey), "AES"),
                new GCMParameterSpec(TAG_BYTES * 8, iv)
            );
            byte[] ciphertextAndTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            int split = ciphertextAndTag.length - TAG_BYTES;
            return new EncryptedSecret(
                Arrays.copyOfRange(ciphertextAndTag, 0, split),
                iv,
                Arrays.copyOfRange(ciphertextAndTag, split, ciphertextAndTag.length)
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException("API key could not be encrypted", exception);
        }
    }

    public record EncryptedSecret(byte[] encrypted, byte[] iv, byte[] authTag) {}
}
