package com.jobtracker.migrationpoc.ai;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AiEndpointPolicy {
    private static final String DEFAULT_ALLOWED_HOSTS = "api.deepseek.com,api.openai.com";

    private final Environment environment;

    public AiEndpointPolicy(Environment environment) {
        this.environment = environment;
    }

    public URI endpoint(String rawValue) throws Exception {
        URI endpoint = normalize(rawValue);
        String host = endpoint.getHost();
        if (host == null || host.isBlank()) throw new UnsafeEndpointException("API 地址缺少域名");
        if (endpoint.getUserInfo() != null
            || "localhost".equalsIgnoreCase(host)
            || "0.0.0.0".equals(host)
            || host.toLowerCase(Locale.ROOT).endsWith(".local")) {
            throw new UnsafeEndpointException("API 地址不安全");
        }
        Set<String> allowed = allowedHosts();
        if (!allowed.isEmpty() && !allowed.contains(host.toLowerCase(Locale.ROOT))) {
            throw new UnsafeEndpointException("该 API 域名未在服务器允许列表中");
        }
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(AiEndpointPolicy::isPrivateAddress)) {
            throw new UnsafeEndpointException("API 地址不能指向内网");
        }
        return endpoint;
    }

    URI normalize(String rawValue) {
        String text = rawValue == null ? "" : rawValue.trim().replaceAll("/+$", "");
        String normalized;
        if (text.matches("(?i).*/chat/completions$")) normalized = text;
        else if (text.matches("(?i).*/v1$")) normalized = text + "/chat/completions";
        else if ("https://api.deepseek.com".equalsIgnoreCase(text)) normalized = text + "/chat/completions";
        else normalized = text + "/v1/chat/completions";
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new UnsafeEndpointException("API 地址格式无效");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new UnsafeEndpointException("线上 API 地址必须使用 HTTPS");
        }
        return uri;
    }

    static boolean isPrivateAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
            || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    private Set<String> allowedHosts() {
        return Arrays.stream(environment.getProperty("AI_ALLOWED_HOSTS", DEFAULT_ALLOWED_HOSTS).split(","))
            .map(String::trim)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .filter(value -> !value.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }

    public static class UnsafeEndpointException extends RuntimeException {
        public UnsafeEndpointException(String message) { super(message); }
    }
}
