package com.jobtracker.migrationpoc.web;

import com.jobtracker.migrationpoc.backup.BackupDocumentValidator.InvalidBackupException;
import com.jobtracker.migrationpoc.database.BackupSandboxService;
import com.jobtracker.migrationpoc.database.BackupSandboxService.BackupConflictException;
import com.jobtracker.migrationpoc.database.BackupSandboxService.BackupNotFoundException;
import com.jobtracker.migrationpoc.database.BackupSandboxService.SandboxDataNotFoundException;
import com.jobtracker.migrationpoc.database.BackupSandboxService.SandboxDisabledException;
import com.jobtracker.migrationpoc.database.LegacyReadService.LegacyUser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/poc/backup-sandbox")
public class PocBackupSandboxController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PocBackupSandboxController.class);

    private final PocAuthController authController;
    private final BackupSandboxService sandboxService;

    public PocBackupSandboxController(PocAuthController authController, BackupSandboxService sandboxService) {
        this.authController = authController;
        this.sandboxService = sandboxService;
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sandboxService.status());
    }

    @GetMapping("/backups")
    public ResponseEntity<?> backups(
        @CookieValue(value = PocAuthController.COOKIE_NAME, required = false) String token
    ) {
        try {
            Optional<LegacyUser> user = authController.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(sandboxService.backups(user.get().email()));
        } catch (Exception exception) {
            return mapException("list", exception);
        }
    }

    @PostMapping("/backups/{id}/restore")
    public ResponseEntity<?> restore(
        @CookieValue(value = PocAuthController.COOKIE_NAME, required = false) String token,
        @PathVariable long id,
        @RequestBody RestoreRequest body,
        HttpServletRequest request
    ) {
        if (!sameOrigin(request)) return error(HttpStatus.FORBIDDEN, "请求来源无效");
        if (body == null || !"恢复".equals(body.confirmation())) {
            return error(HttpStatus.BAD_REQUEST, "请输入“恢复”确认操作");
        }
        try {
            Optional<LegacyUser> user = authController.authenticatedUser(token);
            if (user.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "请先登录");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                sandboxService.restore(user.get().email(), id, body.expectedCurrentUpdatedAt())
            );
        } catch (Exception exception) {
            return mapException("restore", exception);
        }
    }

    private ResponseEntity<?> mapException(String operation, Exception exception) {
        if (exception instanceof BackupConflictException) return error(HttpStatus.CONFLICT, exception.getMessage());
        if (exception instanceof BackupNotFoundException || exception instanceof SandboxDataNotFoundException) {
            return error(HttpStatus.NOT_FOUND, exception.getMessage());
        }
        if (exception instanceof InvalidBackupException) return error(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
        if (exception instanceof SandboxDisabledException) return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        LOGGER.warn("POC backup sandbox {} failed", operation, exception);
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

    public record RestoreRequest(String expectedCurrentUpdatedAt, String confirmation) {}
}
