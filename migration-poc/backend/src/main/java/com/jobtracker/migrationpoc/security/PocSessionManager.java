package com.jobtracker.migrationpoc.security;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Component
public class PocSessionManager {
    private static final Duration SESSION_TTL = Duration.ofHours(4);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final Environment environment;

    public PocSessionManager(Environment environment) {
        this.environment = environment;
    }

    public boolean isConfigured() {
        String secret = secret();
        return secret != null && secret.length() >= 32;
    }

    public Duration sessionTtl() {
        return SESSION_TTL;
    }

    public String issue(long userId) {
        if (!isConfigured() || userId <= 0) throw new IllegalStateException("POC session auth is not configured");
        long expiresAt = Instant.now().plus(SESSION_TTL).getEpochSecond();
        String payload = ENCODER.encodeToString((userId + ":" + expiresAt).getBytes(StandardCharsets.UTF_8));
        return payload + "." + ENCODER.encodeToString(sign(payload));
    }

    public Optional<SessionIdentity> verify(String token) {
        if (!isConfigured() || token == null || token.isBlank()) return Optional.empty();
        try {
            String[] parts = token.split("\\.", 2);
            if (parts.length != 2) return Optional.empty();
            byte[] candidate = DECODER.decode(parts[1]);
            if (!MessageDigest.isEqual(sign(parts[0]), candidate)) return Optional.empty();
            String payload = new String(DECODER.decode(parts[0]), StandardCharsets.UTF_8);
            String[] values = payload.split(":", 2);
            if (values.length != 2) return Optional.empty();
            long userId = Long.parseLong(values[0]);
            long expiresAt = Long.parseLong(values[1]);
            if (userId <= 0 || Instant.now().getEpochSecond() >= expiresAt) return Optional.empty();
            return Optional.of(new SessionIdentity(userId, expiresAt));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign POC session", exception);
        }
    }

    private String secret() {
        return environment.getProperty("POC_SESSION_SECRET");
    }

    public record SessionIdentity(long userId, long expiresAt) {}
}
