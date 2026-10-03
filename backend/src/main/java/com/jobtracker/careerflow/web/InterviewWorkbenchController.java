package com.jobtracker.careerflow.web;

import com.jobtracker.careerflow.database.AiService.AiDisabledException;
import com.jobtracker.careerflow.database.AiService.AiRateLimitException;
import com.jobtracker.careerflow.database.AiService.AiResponseException;
import com.jobtracker.careerflow.database.AiService.AiValidationException;
import com.jobtracker.careerflow.database.InterviewWorkbenchService;
import com.jobtracker.careerflow.database.InterviewWorkbenchService.WorkbenchConflictException;
import com.jobtracker.careerflow.database.LegacyReadService.LegacyUser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/poc/interview-workbench")
public class InterviewWorkbenchController {
    private static final Logger LOGGER = LoggerFactory.getLogger(InterviewWorkbenchController.class);
    private final AuthController auth;
    private final InterviewWorkbenchService workbench;

    public InterviewWorkbenchController(AuthController auth, InterviewWorkbenchService workbench) {
        this.auth = auth;
        this.workbench = workbench;
    }

    @GetMapping
    public ResponseEntity<?> state(@CookieValue(value = AuthController.COOKIE_NAME, required = false) String token) {
        try {
            Optional<LegacyUser> user = auth.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(workbench.state(user.get().email()));
        } catch (Exception exception) { return mapException("read", exception); }
    }

    @PutMapping("/resume")
    public ResponseEntity<?> saveResume(
        @CookieValue(value = AuthController.COOKIE_NAME, required = false) String token,
        @RequestBody JsonNode resume, HttpServletRequest request
    ) {
        if (!sameOrigin(request)) return error(HttpStatus.FORBIDDEN, "请求来源无效");
        try {
            Optional<LegacyUser> user = auth.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(workbench.saveResume(user.get().email(), resume));
        } catch (Exception exception) { return mapException("resume", exception); }
    }

    @PostMapping("/summarize")
    public ResponseEntity<?> summarize(
        @CookieValue(value = AuthController.COOKIE_NAME, required = false) String token,
        HttpServletRequest request
    ) {
        if (!sameOrigin(request)) return error(HttpStatus.FORBIDDEN, "请求来源无效");
        try {
            Optional<LegacyUser> user = auth.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(workbench.summarize(user.get().email()));
        } catch (Exception exception) { return mapException("summarize", exception); }
    }

    private ResponseEntity<?> mapException(String operation, Exception exception) {
        if (exception instanceof AiValidationException) return error(HttpStatus.BAD_REQUEST, exception.getMessage());
        if (exception instanceof WorkbenchConflictException) return error(HttpStatus.CONFLICT, exception.getMessage());
        if (exception instanceof AiRateLimitException) return error(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
        if (exception instanceof AiDisabledException) return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        if (exception instanceof AiResponseException) return error(HttpStatus.BAD_GATEWAY, exception.getMessage());
        LOGGER.warn("Interview workbench {} failed", operation, exception);
        return error(HttpStatus.SERVICE_UNAVAILABLE, "面试总结服务暂时不可用");
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
            String scheme = forwarded == null || forwarded.isBlank() ? request.getScheme() : forwarded.split(",", 2)[0].trim();
            String host = request.getHeader("Host");
            return scheme.equalsIgnoreCase(value.getScheme()) && host != null && host.equalsIgnoreCase(value.getRawAuthority());
        } catch (IllegalArgumentException exception) { return false; }
    }
}
