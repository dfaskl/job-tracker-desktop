package com.jobtracker.migrationpoc.compat;

import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class LegacyPasswordVerifier {
    private static final int COST = 16_384;
    private static final int BLOCK_SIZE = 8;
    private static final int PARALLELIZATION = 1;
    private static final int KEY_LENGTH = 64;

    public boolean verify(String password, String salt, String expectedHex) {
        if (password == null || salt == null || expectedHex == null) return false;
        try {
            byte[] expected = HexFormat.of().parseHex(expectedHex);
            byte[] actual = SCrypt.generate(
                password.getBytes(StandardCharsets.UTF_8),
                salt.getBytes(StandardCharsets.UTF_8),
                COST,
                BLOCK_SIZE,
                PARALLELIZATION,
                KEY_LENGTH
            );
            return MessageDigest.isEqual(actual, expected);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
