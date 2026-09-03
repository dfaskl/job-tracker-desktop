package com.jobtracker.migrationpoc.web;

import com.jobtracker.migrationpoc.compat.LegacyPasswordVerifier;
import com.jobtracker.migrationpoc.database.LegacyReadService;
import com.jobtracker.migrationpoc.database.AccountSandboxService;
import com.jobtracker.migrationpoc.database.LegacyReadService.LegacyUser;
import com.jobtracker.migrationpoc.security.PocPersistentSessionStore;
import com.jobtracker.migrationpoc.security.PocSessionManager;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/poc/auth")
public class PocAuthController {
    public static final String COOKIE_NAME = "poc_session";

    private static final Logger LOGGER = LoggerFactory.getLogger(PocAuthController.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final long ATTEMPT_WINDOW_MILLIS = Duration.ofMinutes(15).toMillis();
    private static final int MAX_ATTEMPTS = 12;

    private final LegacyReadService legacyReadService;
    private final AccountSandboxService accountSandboxService;
    private final LegacyPasswordVerifier passwordVerifier;
    private final PocSessionManager sessionManager;
    private final PocPersistentSessionStore persistentSessionStore;
    private final ConcurrentHashMap<String, Deque<Long>> loginAttempts = new ConcurrentHashMap<>();

    @Autowired
    public PocAuthController(
        LegacyReadService legacyReadService,
        LegacyPasswordVerifier passwordVerifier,
        PocSessionManager sessionManager,
        PocPersistentSessionStore persistentSessionStore,
        AccountSandboxService accountSandboxService
    ) {
        this.legacyReadService = legacyReadService;
        this.accountSandboxService = accountSandboxService;
        this.passwordVerifier = passwordVerifier;
        this.sessionManager = sessionManager;
        this.persistentSessionStore = persistentSessionStore;
    }

    PocAuthController(
        LegacyReadService legacyReadService,
        LegacyPasswordVerifier passwordVerifier,
        PocSessionManager sessionManager,
        PocPersistentSessionStore persistentSessionStore
    ) {
        this(legacyReadService, passwordVerifier, sessionManager, persistentSessionStore, null);
    }
    PocAuthController(
        LegacyReadService legacyReadService,
        LegacyPasswordVerifier passwordVerifier,
        PocSessionManager sessionManager
    ) {
        this(legacyReadService, passwordVerifier, sessionManager, null, null);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest body, HttpServletRequest request) {
        if (!sameOrigin(request)) return error(HttpStatus.FORBIDDEN, "请求来源无效");
        if (!ready()) return error(HttpStatus.SERVICE_UNAVAILABLE, "服务器尚未配置只读登录所需环境变量");

        String remote = remoteAddress(request);
        if (!allowLoginAttempt(remote)) return error(HttpStatus.TOO_MANY_REQUESTS, "尝试次数过多，请稍后再试");

        String email = normalizeEmail(body == null ? null : body.email());
        String password = body == null || body.password() == null ? "" : body.password();
        if (!EMAIL_PATTERN.matcher(email).matches() || password.isBlank() || password.length() > 128) {
            return error(HttpStatus.UNAUTHORIZED, "邮箱或密码不正确");
        }

        try {
            Optional<LegacyUser> found = findUserByEmail(email);
            if (found.isEmpty()
                || found.get().disabled()
                || !passwordVerifier.verify(password, found.get().passwordSalt(), found.get().passwordHash())) {
                return error(HttpStatus.UNAUTHORIZED, "邮箱或密码不正确");
            }

            loginAttempts.remove(remote);
            boolean persistent = persistentSessionEnabled();
            String token = persistent ? persistentSessionStore.issue(found.get().email()) : sessionManager.issue(found.get().id());
            Duration ttl = persistent ? persistentSessionStore.sessionTtl() : sessionManager.sessionTtl();
            return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, sessionCookie(token, ttl, request))
                .body(Map.of("user", publicUser(found.get()), "readOnly", !sandboxAccountsEnabled(), "sessionMode", sessionMode()));
        } catch (Exception exception) {
            LOGGER.warn("POC login failed because the legacy database is unavailable", exception);
            return error(HttpStatus.SERVICE_UNAVAILABLE, "数据库暂时不可用");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest body, HttpServletRequest request) {
        if (!sameOrigin(request)) return error(HttpStatus.FORBIDDEN, "请求来源无效");
        if (!sandboxAccountsEnabled()) return error(HttpStatus.SERVICE_UNAVAILABLE, "独立测试数据库注册未开启");
        String email = normalizeEmail(body == null ? null : body.email());
        String password = body == null || body.password() == null ? "" : body.password();
        try {
            LegacyUser user = accountSandboxService.register(email, password, body == null ? null : body.registrationCode());
            boolean persistent = persistentSessionEnabled();
            String token = persistent ? persistentSessionStore.issue(user.email()) : sessionManager.issue(user.id());
            Duration ttl = persistent ? persistentSessionStore.sessionTtl() : sessionManager.sessionTtl();
            return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, sessionCookie(token, ttl, request))
                .body(Map.of("user", publicUser(user), "readOnly", false, "sessionMode", sessionMode()));
        } catch (AccountSandboxService.AccountValidationException e) { return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (AccountSandboxService.AccountForbiddenException e) { return error(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (AccountSandboxService.AccountConflictException e) { return error(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) { LOGGER.warn("POC registration failed", e); return error(HttpStatus.SERVICE_UNAVAILABLE, "注册服务暂时不可用"); }
    }
    @GetMapping("/session")
    public ResponseEntity<?> session(@CookieValue(value = COOKIE_NAME, required = false) String token) {
        if (!ready()) return error(HttpStatus.SERVICE_UNAVAILABLE, "服务器尚未配置只读登录所需环境变量");
        try {
            Optional<LegacyUser> user = authenticatedUser(token);
            return user.<ResponseEntity<?>>map(value -> ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of("user", publicUser(value), "readOnly", !sandboxAccountsEnabled(), "sessionMode", sessionMode())))
                .orElseGet(() -> error(HttpStatus.UNAUTHORIZED, "请先登录"));
        } catch (Exception exception) {
            LOGGER.warn("POC session check failed because the legacy database is unavailable", exception);
            return error(HttpStatus.SERVICE_UNAVAILABLE, "数据库暂时不可用");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
        @CookieValue(value = COOKIE_NAME, required = false) String token,
        HttpServletRequest request
    ) {
        if (!sameOrigin(request)) return error(HttpStatus.FORBIDDEN, "请求来源无效");
        if (persistentSessionEnabled()) {
            try {
                persistentSessionStore.revoke(token);
            } catch (Exception exception) {
                LOGGER.warn("POC persistent session revocation failed", exception);
            }
        }
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO, request))
            .body(Map.of("ok", true));
    }

    Optional<LegacyUser> authenticatedUser(String token) throws Exception {
        if (persistentSessionEnabled()) {
            Optional<String> email = persistentSessionStore.verifyEmail(token);
            if (email.isEmpty()) return Optional.empty();
            return findUserByEmail(email.get()).filter(value -> !value.disabled());
        }
        Optional<PocSessionManager.SessionIdentity> identity = sessionManager.verify(token);
        if (identity.isEmpty()) return Optional.empty();
        return findUserById(identity.get().userId()).filter(value -> !value.disabled());
    }

    private boolean sandboxAccountsEnabled() { return accountSandboxService != null && accountSandboxService.enabled(); }
    private Optional<LegacyUser> findUserByEmail(String email) throws Exception { return sandboxAccountsEnabled() ? accountSandboxService.findByEmail(email) : legacyReadService.findUserByEmail(email); }
    private Optional<LegacyUser> findUserById(long id) throws Exception { return sandboxAccountsEnabled() ? accountSandboxService.findById(id) : legacyReadService.findUserById(id); }
    private boolean ready() {
        return (sandboxAccountsEnabled() || legacyReadService.isConfigured()) && (persistentSessionEnabled() || sessionManager.isConfigured());
    }

    private boolean persistentSessionEnabled() {
        return persistentSessionStore != null && persistentSessionStore.isEnabled();
    }

    private String sessionMode() {
        return persistentSessionEnabled() ? "postgresql" : "signed-cookie";
    }

    private Map<String, Object> publicUser(LegacyUser user) {
        return Map.of("id", String.valueOf(user.id()), "email", user.email());
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("message", message));
    }

    private String sessionCookie(String token, Duration maxAge, HttpServletRequest request) {
        return ResponseCookie.from(COOKIE_NAME, token)
            .httpOnly(true)
            .secure(isSecure(request))
            .sameSite("Strict")
            .path("/")
            .maxAge(maxAge)
            .build()
            .toString();
    }

    private boolean allowLoginAttempt(String remote) {
        long now = System.currentTimeMillis();
        Deque<Long> attempts = loginAttempts.computeIfAbsent(remote, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            while (!attempts.isEmpty() && now - attempts.peekFirst() >= ATTEMPT_WINDOW_MILLIS) attempts.removeFirst();
            if (attempts.size() >= MAX_ATTEMPTS) return false;
            attempts.addLast(now);
            return true;
        }
    }

    private String remoteAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",", 2)[0].trim();
        return request.getRemoteAddr();
    }

    private String normalizeEmail(String value) {
        String email = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return email.length() <= 254 ? email : email.substring(0, 254);
    }

    private boolean isSecure(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-Proto");
        if (forwarded != null && !forwarded.isBlank()) {
            return "https".equalsIgnoreCase(forwarded.split(",", 2)[0].trim());
        }
        return request.isSecure();
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

    public record LoginRequest(String email, String password) {}
    public record RegisterRequest(String email, String password, String registrationCode) {}
}
