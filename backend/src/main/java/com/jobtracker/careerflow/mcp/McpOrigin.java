package com.jobtracker.careerflow.mcp;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;

public final class McpOrigin {
    private McpOrigin() { }

    public static boolean allowed(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) return true;
        try {
            URI uri = URI.create(origin);
            String forwarded = request.getHeader("X-Forwarded-Proto");
            String scheme = forwarded == null ? request.getScheme() : forwarded.split(",", 2)[0].trim();
            return uri.getUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null
                && (uri.getPath() == null || uri.getPath().isEmpty())
                && scheme.equalsIgnoreCase(uri.getScheme()) && uri.getRawAuthority() != null
                && uri.getRawAuthority().equalsIgnoreCase(request.getHeader("Host"));
        } catch (IllegalArgumentException exception) { return false; }
    }
}
