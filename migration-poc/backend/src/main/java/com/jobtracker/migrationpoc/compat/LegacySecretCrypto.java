package com.jobtracker.migrationpoc.compat;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Component
public class LegacySecretCrypto {
    private static final Pattern HEX_KEY = Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final Pattern BASE64_KEY = Pattern.compile("^[A-Za-z0-9+/]+={0,2}$");

    public String decrypt(String configuredKey, byte[] encrypted, byte[] iv, byte[] authTag) {
        try {
            byte[] ciphertextAndTag = new byte[encrypted.length + authTag.length];
            System.arraycopy(encrypted, 0, ciphertextAndTag, 0, encrypted.length);
            System.arraycopy(authTag, 0, ciphertextAndTag, encrypted.length, authTag.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(parseKey(configuredKey), "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertextAndTag), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("Legacy API key ciphertext could not be decrypted", exception);
        }
    }

    byte[] parseKey(String value) {
        String text = value == null ? "" : value.trim();
        if (text.length() < 32) throw new IllegalArgumentException("ENCRYPTION_KEY must contain at least 32 characters");
        if (HEX_KEY.matcher(text).matches()) return HexFormat.of().parseHex(text);
        if (BASE64_KEY.matcher(text).matches()) {
            try {
                byte[] decoded = Base64.getDecoder().decode(text);
                if (decoded.length == 32) return decoded;
            } catch (IllegalArgumentException ignored) {
                // Match the Node implementation: fall back to SHA-256 of the text.
            }
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
