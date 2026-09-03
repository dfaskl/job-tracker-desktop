package com.jobtracker.migrationpoc.web;

import com.jobtracker.migrationpoc.database.LegacyReadService;
import com.jobtracker.migrationpoc.database.LegacyReadService.LegacyUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/poc")
public class PocCompanyLinksController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PocCompanyLinksController.class);

    private final PocAuthController authController;
    private final LegacyReadService legacyReadService;

    public PocCompanyLinksController(PocAuthController authController, LegacyReadService legacyReadService) {
        this.authController = authController;
        this.legacyReadService = legacyReadService;
    }

    @GetMapping("/company-links")
    public ResponseEntity<?> companyLinks(
        @CookieValue(value = PocAuthController.COOKIE_NAME, required = false) String token
    ) {
        try {
            Optional<LegacyUser> user = authController.authenticatedUser(token);
            if (user.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).cacheControl(CacheControl.noStore())
                    .body(Map.of("message", "请先登录"));
            }
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(legacyReadService.findCompanyLinks(user.get().id()));
        } catch (Exception exception) {
            LOGGER.warn("POC company links read failed because the legacy database is unavailable", exception);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore())
                .body(Map.of("message", "数据库暂时不可用"));
        }
    }
}