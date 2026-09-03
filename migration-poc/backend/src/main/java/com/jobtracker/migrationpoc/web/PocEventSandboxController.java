package com.jobtracker.migrationpoc.web;

import com.jobtracker.migrationpoc.database.EventSandboxService;
import com.jobtracker.migrationpoc.database.EventSandboxService.SandboxDataNotFoundException;
import com.jobtracker.migrationpoc.database.EventSandboxService.SandboxDisabledException;
import com.jobtracker.migrationpoc.database.LegacyReadService.LegacyUser;
import com.jobtracker.migrationpoc.event.EventDocumentMutator.ConflictException;
import com.jobtracker.migrationpoc.event.EventDocumentMutator.EventInput;
import com.jobtracker.migrationpoc.event.EventDocumentMutator.EventView;
import com.jobtracker.migrationpoc.event.EventDocumentMutator.NotFoundException;
import com.jobtracker.migrationpoc.event.EventDocumentMutator.Resolution;
import com.jobtracker.migrationpoc.event.EventDocumentMutator.ValidationException;
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
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/poc/event-sandbox")
public class PocEventSandboxController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PocEventSandboxController.class);

    private final PocAuthController authController;
    private final EventSandboxService sandboxService;

    public PocEventSandboxController(PocAuthController authController, EventSandboxService sandboxService) {
        this.authController = authController;
        this.sandboxService = sandboxService;
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sandboxService.status());
    }

    @GetMapping("/events")
    public ResponseEntity<?> events(
        @CookieValue(value = PocAuthController.COOKIE_NAME, required = false) String token
    ) {
        try {
            Optional<LegacyUser> user = authController.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            var page = sandboxService.findEvents(user.get().email());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new EventsResponse(
                page.events(), page.applications(), page.total(), page.truncated(), true
            ));
        } catch (Exception exception) {
            return mapException("read", exception);
        }
    }

    @PostMapping("/events")
    public ResponseEntity<?> create(
        @CookieValue(value = PocAuthController.COOKIE_NAME, required = false) String token,
        @RequestBody EventWriteRequest body,
        HttpServletRequest request
    ) {
        if (!sameOrigin(request)) return error(HttpStatus.FORBIDDEN, "请求来源无效");
        try {
            Optional<LegacyUser> user = authController.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            var mutation = sandboxService.create(user.get().email(), input(body));
            return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(new MutationResponse(mutation.event(), mutation.total(), true));
        } catch (Exception exception) {
            return mapException("create", exception);
        }
    }

    @PutMapping("/events/{id}")
    public ResponseEntity<?> update(
        @CookieValue(value = PocAuthController.COOKIE_NAME, required = false) String token,
        @PathVariable String id,
        @RequestBody EventWriteRequest body,
        HttpServletRequest request
    ) {
        if (!sameOrigin(request)) return error(HttpStatus.FORBIDDEN, "请求来源无效");
        try {
            Optional<LegacyUser> user = authController.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            var mutation = sandboxService.update(user.get().email(), id, input(body), body.expectedUpdatedAt());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new MutationResponse(mutation.event(), mutation.total(), true));
        } catch (Exception exception) {
            return mapException("update", exception);
        }
    }

    @PostMapping("/events/{id}/resolution")
    public ResponseEntity<?> resolve(
        @CookieValue(value = PocAuthController.COOKIE_NAME, required = false) String token,
        @PathVariable String id,
        @RequestBody ResolutionRequest body,
        HttpServletRequest request
    ) {
        if (!sameOrigin(request)) return error(HttpStatus.FORBIDDEN, "请求来源无效");
        try {
            Optional<LegacyUser> user = authController.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            Resolution resolution = resolution(body == null ? null : body.action());
            var mutation = sandboxService.resolve(
                user.get().email(), id, resolution, body == null ? null : body.expectedUpdatedAt()
            );
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new MutationResponse(mutation.event(), mutation.total(), true));
        } catch (Exception exception) {
            return mapException("resolve", exception);
        }
    }

    @DeleteMapping("/events/{id}")
    public ResponseEntity<?> delete(
        @CookieValue(value = PocAuthController.COOKIE_NAME, required = false) String token,
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
                .body(new MutationResponse(mutation.event(), mutation.total(), true));
        } catch (Exception exception) {
            return mapException("delete", exception);
        }
    }

    private EventInput input(EventWriteRequest body) {
        if (body == null) return null;
        return new EventInput(
            body.applicationId(), body.type(), body.title(), body.startsAt(), body.endsAt(),
            body.location(), body.notes()
        );
    }

    private Resolution resolution(String action) {
        try {
            return Resolution.valueOf(action == null ? "" : action.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("日程操作必须为 complete、miss 或 restore");
        }
    }

    private ResponseEntity<?> mapException(String operation, Exception exception) {
        if (exception instanceof ValidationException) return error(HttpStatus.BAD_REQUEST, exception.getMessage());
        if (exception instanceof NotFoundException || exception instanceof SandboxDataNotFoundException) {
            return error(HttpStatus.NOT_FOUND, exception.getMessage());
        }
        if (exception instanceof ConflictException) return error(HttpStatus.CONFLICT, exception.getMessage());
        if (exception instanceof SandboxDisabledException) return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        LOGGER.warn("POC event sandbox {} failed", operation, exception);
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

    public record EventWriteRequest(
        String applicationId,
        String type,
        String title,
        String startsAt,
        String endsAt,
        String location,
        String notes,
        String expectedUpdatedAt
    ) {}

    public record ResolutionRequest(String action, String expectedUpdatedAt) {}
    public record DeleteRequest(String expectedUpdatedAt) {}
    public record EventsResponse(Object events, Object applications, int total, boolean truncated, boolean sandbox) {}
    public record MutationResponse(EventView event, int total, boolean sandbox) {}
}
