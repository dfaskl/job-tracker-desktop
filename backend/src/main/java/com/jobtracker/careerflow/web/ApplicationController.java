package com.jobtracker.careerflow.web;

import com.jobtracker.careerflow.application.ApplicationDocumentMutator.ApplicationInput;
import com.jobtracker.careerflow.application.ApplicationDocumentMutator.ApplicationView;
import com.jobtracker.careerflow.application.ApplicationDocumentMutator.ConflictException;
import com.jobtracker.careerflow.application.ApplicationDocumentMutator.NotFoundException;
import com.jobtracker.careerflow.application.ApplicationDocumentMutator.ValidationException;
import com.jobtracker.careerflow.database.ApplicationService;
import com.jobtracker.careerflow.database.ApplicationService.ApplicationPage;
import com.jobtracker.careerflow.database.ApplicationService.SandboxDataNotFoundException;
import com.jobtracker.careerflow.database.ApplicationService.SandboxDisabledException;
import com.jobtracker.careerflow.database.LegacyReadService.LegacyUser;
import com.jobtracker.careerflow.event.EventDocumentMutator.EventInput;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/poc/application-sandbox")
public class ApplicationController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationController.class);

    private final AuthController authController;
    private final ApplicationService sandboxService;

    public ApplicationController(AuthController authController, ApplicationService sandboxService) {
        this.authController = authController;
        this.sandboxService = sandboxService;
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sandboxService.status());
    }

    @GetMapping("/applications")
    public ResponseEntity<?> applications(
        @CookieValue(value = AuthController.COOKIE_NAME, required = false) String token
    ) {
        try {
            Optional<LegacyUser> user = authController.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            ApplicationPage page = sandboxService.findApplications(user.get().email());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ApplicationsResponse(
                page.applications(), page.total(), page.truncated(), true
            ));
        } catch (Exception exception) {
            return mapException("read", exception);
        }
    }

    @PostMapping("/applications")
    public ResponseEntity<?> create(
        @CookieValue(value = AuthController.COOKIE_NAME, required = false) String token,
        @RequestBody ApplicationWriteRequest body,
        HttpServletRequest request
    ) {
        if (!sameOrigin(request)) return error(HttpStatus.FORBIDDEN, "请求来源无效");
        try {
            Optional<LegacyUser> user = authController.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            var mutation = sandboxService.create(user.get().email(), input(body), schedule(body));
            return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(new MutationResponse(mutation.application(), mutation.total(), true));
        } catch (Exception exception) {
            return mapException("create", exception);
        }
    }

    @PutMapping("/applications/{id}")
    public ResponseEntity<?> update(
        @CookieValue(value = AuthController.COOKIE_NAME, required = false) String token,
        @PathVariable String id,
        @RequestBody ApplicationWriteRequest body,
        HttpServletRequest request
    ) {
        if (!sameOrigin(request)) return error(HttpStatus.FORBIDDEN, "请求来源无效");
        try {
            Optional<LegacyUser> user = authController.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            var mutation = sandboxService.update(user.get().email(), id, input(body), body.expectedUpdatedAt());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new MutationResponse(mutation.application(), mutation.total(), true));
        } catch (Exception exception) {
            return mapException("update", exception);
        }
    }

    @DeleteMapping("/applications/{id}")
    public ResponseEntity<?> delete(
        @CookieValue(value = AuthController.COOKIE_NAME, required = false) String token,
        @PathVariable String id,
        @RequestBody(required = false) DeleteRequest body,
        HttpServletRequest request
    ) {
        if (!sameOrigin(request)) return error(HttpStatus.FORBIDDEN, "请求来源无效");
        try {
            Optional<LegacyUser> user = authController.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            var mutation = sandboxService.delete(
                user.get().email(), id, body == null ? null : body.expectedUpdatedAt()
            );
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new MutationResponse(mutation.application(), mutation.total(), true));
        } catch (Exception exception) {
            return mapException("delete", exception);
        }
    }

    private ApplicationInput input(ApplicationWriteRequest body) {
        if (body == null) return null;
        return new ApplicationInput(
            body.company(), body.position(), body.city(), body.channel(), body.appliedDate(),
            body.stage(), body.status(), body.notes()
        );
    }

    private EventInput schedule(ApplicationWriteRequest body) {
        if (body == null || body.scheduleStartsAt() == null || body.scheduleStartsAt().isBlank()) return null;
        return new EventInput(
            "", body.scheduleType(), body.scheduleTitle(), body.scheduleStartsAt(), body.scheduleEndsAt(),
            body.scheduleLocation(), body.scheduleNotes()
        );
    }

    private ResponseEntity<?> mapException(String operation, Exception exception) {
        if (exception instanceof ValidationException) return error(HttpStatus.BAD_REQUEST, exception.getMessage());
        if (exception instanceof NotFoundException || exception instanceof SandboxDataNotFoundException) {
            return error(HttpStatus.NOT_FOUND, exception.getMessage());
        }
        if (exception instanceof ConflictException) return error(HttpStatus.CONFLICT, exception.getMessage());
        if (exception instanceof SandboxDisabledException) return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        LOGGER.warn("POC application sandbox {} failed", operation, exception);
        return error(HttpStatus.SERVICE_UNAVAILABLE, "测试数据库暂时不可用");
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

    public record ApplicationWriteRequest(
        String company,
        String position,
        String city,
        String channel,
        String appliedDate,
        String stage,
        String status,
        String notes,
        String expectedUpdatedAt,
        String scheduleType,
        String scheduleTitle,
        String scheduleStartsAt,
        String scheduleEndsAt,
        String scheduleLocation,
        String scheduleNotes
    ) {}

    public record DeleteRequest(String expectedUpdatedAt) {}
    public record ApplicationsResponse(List<ApplicationView> applications, int total, boolean truncated, boolean sandbox) {}
    public record MutationResponse(ApplicationView application, int total, boolean sandbox) {}
}
