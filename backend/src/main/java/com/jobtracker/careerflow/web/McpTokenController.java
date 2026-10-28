package com.jobtracker.careerflow.web;

import com.jobtracker.careerflow.mcp.McpOrigin;
import com.jobtracker.careerflow.mcp.McpTokenStore;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/poc/mcp")
public class McpTokenController {
    private static final Logger LOGGER = LoggerFactory.getLogger(McpTokenController.class);
    private final AuthController auth;
    private final McpTokenStore tokens;
    public McpTokenController(AuthController auth, McpTokenStore tokens) { this.auth = auth; this.tokens = tokens; }

    @GetMapping("/tokens")
    public ResponseEntity<?> list(@CookieValue(value = AuthController.COOKIE_NAME, required = false) String session) {
        return withUser(session, email -> Map.of("items", tokens.list(email), "audit", tokens.audit(email)));
    }
    @PostMapping("/tokens")
    public ResponseEntity<?> create(@CookieValue(value = AuthController.COOKIE_NAME, required = false) String session,
                                    @RequestBody CreateRequest body, HttpServletRequest request) {
        if (!McpOrigin.allowed(request)) return error(403, "请求来源无效");
        return withUser(session, email -> tokens.create(email, body == null ? null : body.name(), body == null ? null : body.days(), body == null ? null : body.scopes()));
    }
    @DeleteMapping("/tokens/{id}")
    public ResponseEntity<?> revoke(@CookieValue(value = AuthController.COOKIE_NAME, required = false) String session,
                                    @PathVariable String id, HttpServletRequest request) {
        if (!McpOrigin.allowed(request)) return error(403, "请求来源无效");
        return withUser(session, email -> {
            if (!tokens.revoke(email, id)) return Map.of("revoked", false);
            return Map.of("revoked", true);
        });
    }
    private ResponseEntity<?> withUser(String session, Action action) {
        try {
            var user = auth.authenticatedUser(session);
            if (user.isEmpty()) return error(401, "请先登录");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(action.run(user.get().email()));
        } catch (IllegalArgumentException exception) { return error(400, exception.getMessage()); }
        catch (Exception exception) {
            LOGGER.warn("MCP token management failed", exception);
            return error(503, "访问令牌服务暂时不可用");
        }
    }
    private ResponseEntity<?> error(int status, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("message", message));
    }
    private interface Action { Object run(String email) throws Exception; }
    public record CreateRequest(String name, Integer days, List<String> scopes) { }
}
