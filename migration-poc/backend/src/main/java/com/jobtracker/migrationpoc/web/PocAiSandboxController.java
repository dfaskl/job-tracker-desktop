package com.jobtracker.migrationpoc.web;

import com.jobtracker.migrationpoc.ai.AiEndpointPolicy.UnsafeEndpointException;
import com.jobtracker.migrationpoc.database.AiSandboxService;
import com.jobtracker.migrationpoc.database.AiSandboxService.AiDisabledException;
import com.jobtracker.migrationpoc.database.AiSandboxService.AiRateLimitException;
import com.jobtracker.migrationpoc.database.AiSandboxService.AiResponseException;
import com.jobtracker.migrationpoc.database.AiSandboxService.AiValidationException;
import com.jobtracker.migrationpoc.database.LegacyReadService.LegacyUser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/poc/ai-sandbox")
public class PocAiSandboxController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PocAiSandboxController.class);

    private final PocAuthController authController;
    private final AiSandboxService sandboxService;

    public PocAiSandboxController(PocAuthController authController, AiSandboxService sandboxService) {
        this.authController = authController;
        this.sandboxService = sandboxService;
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sandboxService.status());
    }

    @GetMapping("/config")
    public ResponseEntity<?> config(
        @CookieValue(value = PocAuthController.COOKIE_NAME, required = false) String token
    ) {
        try {
            Optional<LegacyUser> user = authController.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(sandboxService.config(user.get().email()));
        } catch (Exception exception) {
            return mapException("config-read", exception);
        }
    }

    @PostMapping("/config")
    public ResponseEntity<?> saveConfig(
        @CookieValue(value = PocAuthController.COOKIE_NAME, required = false) String token,
        @RequestBody ConfigRequest body,
        HttpServletRequest request
    ) {
        if (!sameOrigin(request)) return error(HttpStatus.FORBIDDEN, "请求来源无效");
        try {
            Optional<LegacyUser> user = authController.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            if (body == null) throw new AiValidationException("请求内容不能为空");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sandboxService.saveConfig(
                user.get().email(), body.apiUrl(), body.model(), body.apiKey(), body.clearApiKey()
            ));
        } catch (Exception exception) {
            return mapException("config-write", exception);
        }
    }

    @PostMapping("/recognize")
    public ResponseEntity<?> recognize(
        @CookieValue(value = PocAuthController.COOKIE_NAME, required = false) String token,
        @RequestBody RecognitionRequest body,
        HttpServletRequest request
    ) {
        if (!sameOrigin(request)) return error(HttpStatus.FORBIDDEN, "请求来源无效");
        try {
            Optional<LegacyUser> user = authController.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                sandboxService.recognize(user.get().email(), body == null ? null : body.body())
            );
        } catch (Exception exception) {
            return mapException("recognize", exception);
        }
    }

    @PostMapping("/normalize-application")
    public ResponseEntity<?> normalizeApplication(
        @CookieValue(value = PocAuthController.COOKIE_NAME, required = false) String token,
        @RequestBody NormalizeRequest body,
        HttpServletRequest request
    ) {
        if (!sameOrigin(request)) return error(HttpStatus.FORBIDDEN, "请求来源无效");
        try {
            Optional<LegacyUser> user = authController.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                sandboxService.normalizeApplication(user.get().email(), body == null ? null : body.application())
            );
        } catch (Exception exception) { return mapException("normalize-application", exception); }
    }
    @PostMapping("/schedule-advice")
    public ResponseEntity<?> scheduleAdvice(
        @CookieValue(value = PocAuthController.COOKIE_NAME, required = false) String token,
        @RequestBody ScheduleAdviceRequest body,
        HttpServletRequest request
    ) {
        if (!sameOrigin(request)) return error(HttpStatus.FORBIDDEN, "请求来源无效");
        try {
            Optional<LegacyUser> user = authController.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                sandboxService.scheduleAdvice(user.get().email(), body == null ? null : body.schedules())
            );
        } catch (Exception exception) {
            return mapException("schedule-advice", exception);
        }
    }
    @PostMapping("/daily-quote")
    public ResponseEntity<?> dailyQuote(
        @CookieValue(value = PocAuthController.COOKIE_NAME, required = false) String token,
        @RequestBody(required = false) DailyQuoteRequest body,
        HttpServletRequest request
    ) {
        if (!sameOrigin(request)) return error(HttpStatus.FORBIDDEN, "请求来源无效");
        try {
            Optional<LegacyUser> user = authController.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                sandboxService.dailyQuote(user.get().email(), body == null ? null : body.date())
            );
        } catch (Exception exception) {
            return mapException("daily-quote", exception);
        }
    }
    private ResponseEntity<?> mapException(String operation, Exception exception) {
        if (exception instanceof AiValidationException || exception instanceof UnsafeEndpointException) {
            return error(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        if (exception instanceof AiRateLimitException) return error(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
        if (exception instanceof AiDisabledException) return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        if (exception instanceof AiResponseException) return error(HttpStatus.BAD_GATEWAY, exception.getMessage());
        LOGGER.warn("POC AI sandbox {} failed", operation, exception);
        return error(HttpStatus.SERVICE_UNAVAILABLE, "AI 测试服务暂时不可用");
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("message", message));
    }

    private boolean sameOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) return true;
        try {
            URI value = URI.create(origin);
            String forwarded = request.getHeader("X-Forwarded-Proto");
            String scheme = forwarded == null || forwarded.isBlank()
                ? request.getScheme()
                : forwarded.split(",", 2)[0].trim();
            String host = request.getHeader("Host");
            return scheme.equalsIgnoreCase(value.getScheme())
                && host != null
                && host.equalsIgnoreCase(value.getRawAuthority());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public record ConfigRequest(String apiUrl, String model, String apiKey, boolean clearApiKey) {}
    public record RecognitionRequest(String body) {}
    public record DailyQuoteRequest(String date) {}
    public record ScheduleAdviceRequest(tools.jackson.databind.JsonNode schedules) {}
    public record NormalizeRequest(tools.jackson.databind.JsonNode application) {}
}
