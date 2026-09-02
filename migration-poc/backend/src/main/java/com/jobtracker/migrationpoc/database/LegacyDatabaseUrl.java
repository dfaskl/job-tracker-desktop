package com.jobtracker.migrationpoc.database;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public record LegacyDatabaseUrl(String jdbcUrl, String username, String password) {
    public static LegacyDatabaseUrl parse(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) throw new IllegalArgumentException("DATABASE_URL is not configured");
        if (rawUrl.startsWith("jdbc:postgresql:")) return new LegacyDatabaseUrl(rawUrl, null, null);

        URI uri = URI.create(rawUrl);
        if (!"postgres".equals(uri.getScheme()) && !"postgresql".equals(uri.getScheme())) {
            throw new IllegalArgumentException("DATABASE_URL must use postgres, postgresql, or jdbc:postgresql");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) throw new IllegalArgumentException("DATABASE_URL does not contain a host");
        if (host.contains(":")) host = "[" + host + "]";
        int port = uri.getPort() < 0 ? 5432 : uri.getPort();
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();

        String username = null;
        String password = null;
        if (uri.getRawUserInfo() != null) {
            String[] credentials = uri.getRawUserInfo().split(":", 2);
            username = decode(credentials[0]);
            password = credentials.length == 2 ? decode(credentials[1]) : "";
        }
        return new LegacyDatabaseUrl("jdbc:postgresql://" + host + ":" + port + path + query, username, password);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
