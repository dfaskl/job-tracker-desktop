package com.jobtracker.migrationpoc.config;

import org.springframework.core.env.Environment;

/** Centralized production configuration with compatibility aliases for existing deployments. */
public final class AppEnvironment {
    private AppEnvironment() {}

    public static String databaseUrl(Environment environment) {
        return first(environment, "APP_DATABASE_URL", "POC_WRITE_DATABASE_URL", "DATABASE_URL");
    }

    public static String sessionSecret(Environment environment) {
        return first(environment, "SESSION_SECRET", "POC_SESSION_SECRET");
    }

    public static String encryptionKey(Environment environment) {
        return first(environment, "ENCRYPTION_KEY", "POC_ENCRYPTION_KEY");
    }

    public static String maintenanceAccessToken(Environment environment) {
        return first(environment, "MAINTENANCE_ACCESS_TOKEN", "POC_ACCESS_TOKEN");
    }

    public static boolean persistentSessionsEnabled(Environment environment) {
        return flag(environment, true, "PERSISTENT_SESSION_ENABLED", "POC_PERSISTENT_SESSION_ENABLED");
    }

    public static boolean aiCallsEnabled(Environment environment) {
        return flag(environment, true, "AI_CALLS_ENABLED");
    }

    public static boolean adminEnabled(Environment environment) {
        return flag(environment, true, "ADMIN_ENABLED", "POC_ADMIN_ENABLED");
    }

    public static int sessionDays(Environment environment) {
        String value = first(environment, "SESSION_DAYS", "POC_SESSION_DAYS");
        if (value == null) return 7;
        try {
            return Math.max(1, Math.min(30, Integer.parseInt(value)));
        } catch (NumberFormatException ignored) {
            return 7;
        }
    }

    public static boolean flag(Environment environment, boolean defaultValue, String... names) {
        String value = first(environment, names);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    public static String first(Environment environment, String... names) {
        for (String name : names) {
            String value = environment.getProperty(name);
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }
}
