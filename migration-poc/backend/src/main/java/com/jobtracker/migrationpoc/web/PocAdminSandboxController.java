package com.jobtracker.migrationpoc.web;

import com.jobtracker.migrationpoc.database.AdminSandboxService;
import com.jobtracker.migrationpoc.database.AdminSandboxService.AdminDisabledException;
import com.jobtracker.migrationpoc.database.AdminSandboxService.AdminForbiddenException;
import com.jobtracker.migrationpoc.database.AdminSandboxService.AdminNotFoundException;
import com.jobtracker.migrationpoc.database.AdminSandboxService.AdminValidationException;
import com.jobtracker.migrationpoc.database.LegacyReadService.LegacyUser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/poc/admin-sandbox")
public class PocAdminSandboxController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PocAdminSandboxController.class);

    private final PocAuthController authController;
    private final AdminSandboxService adminService;

    public PocAdminSandboxController(PocAuthController authController, AdminSandboxService adminService) {
        this.authController = authController;
        this.adminService = adminService;
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(adminService.status());
    }

    @GetMapping("/overview")
    public ResponseEntity<?> overview(
        @CookieValue(value = PocAuthController.COOKIE_NAME, required = false) String token
    ) {
        try {
            Optional<LegacyUser> user = authController.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            return ok(adminService.overview(user.get().email()));
        } catch (Exception exception) {
            return mapException("overview", exception);
        }
    }

    @GetMapping("/users/{id}/details")
    public ResponseEntity<?> details(
        @CookieValue(value = PocAuthController.COOKIE_NAME, required = false) String token,
        @PathVariable long id
    ) {
        try {
            Optional<LegacyUser> user = authController.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            return ok(adminService.details(user.get().email(), id));
        } catch (Exception exception) {
            return mapException("details", exception);
        }
    }

    @PatchMapping("/settings/registration")
    public ResponseEntity<?> registration(
        @CookieValue(value = PocAuthController.COOKIE_NAME, required = false) String token,
        @RequestBody(required = false) RegistrationRequest body,
        HttpServletRequest request
    ) {
        if (!sameOrigin(request)) return error(HttpStatus.FORBIDDEN, "请求来源无效");
        if (body == null || body.enabled() == null) return error(HttpStatus.BAD_REQUEST, "注册开关状态不正确");
        try {
            Optional<LegacyUser> user = authController.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            return ok(adminService.setRegistration(user.get().email(), body.enabled()));
        } catch (Exception exception) {
            return mapException("registration", exception);
        }
    }

    @PatchMapping("/users/{id}")
    public ResponseEntity<?> updateUser(
        @CookieValue(value = PocAuthController.COOKIE_NAME, required = false) String token,
        @PathVariable long id,
        @RequestBody(required = false) UserStateRequest body,
        HttpServletRequest request
    ) {
        if (!sameOrigin(request)) return error(HttpStatus.FORBIDDEN, "请求来源无效");
        if (body == null || body.disabled() == null) return error(HttpStatus.BAD_REQUEST, "用户设置内容不正确");
        try {
            Optional<LegacyUser> user = authController.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            return ok(adminService.setDisabled(user.get().email(), id, body.disabled()));
        } catch (Exception exception) {
            return mapException("update-user", exception);
        }
    }

    @PatchMapping("/users/{id}/sessions/revoke")
    public ResponseEntity<?> revokeSessions(
        @CookieValue(value = PocAuthController.COOKIE_NAME, required = false) String token,
        @PathVariable long id,
        HttpServletRequest request
    ) {
        if (!sameOrigin(request)) return error(HttpStatus.FORBIDDEN, "请求来源无效");
        try {
            Optional<LegacyUser> user = authController.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            return ok(adminService.revokeSessions(user.get().email(), id));
        } catch (Exception exception) {
            return mapException("revoke-sessions", exception);
        }
    }
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(
        @CookieValue(value = PocAuthController.COOKIE_NAME, required = false) String token,
        @PathVariable long id,
        @RequestBody(required = false) DeleteUserRequest body,
        HttpServletRequest request
    ) {
        if (!sameOrigin(request)) return error(HttpStatus.FORBIDDEN, "请求来源无效");
        if (body == null || body.confirmEmail() == null || body.confirmEmail().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "请输入目标用户邮箱确认删除");
        }
        try {
            Optional<LegacyUser> user = authController.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            return ok(adminService.deleteUser(user.get().email(), id, body.confirmEmail()));
        } catch (Exception exception) {
            return mapException("delete-user", exception);
        }
    }

    private ResponseEntity<?> mapException(String operation, Exception exception) {
        if (exception instanceof AdminForbiddenException) return error(HttpStatus.FORBIDDEN, exception.getMessage());
        if (exception instanceof AdminNotFoundException) return error(HttpStatus.NOT_FOUND, exception.getMessage());
        if (exception instanceof AdminValidationException) return error(HttpStatus.BAD_REQUEST, exception.getMessage());
        if (exception instanceof AdminDisabledException) return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        LOGGER.warn("POC admin sandbox {} failed", operation, exception);
        return error(HttpStatus.SERVICE_UNAVAILABLE, "测试数据库暂时不可用");
    }

    private ResponseEntity<?> ok(Object body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
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

    public record RegistrationRequest(Boolean enabled) {}
    public record UserStateRequest(Boolean disabled) {}
    public record DeleteUserRequest(String confirmEmail) {}
}
