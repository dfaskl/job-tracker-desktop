package com.jobtracker.migrationpoc.web;

import com.jobtracker.migrationpoc.database.DatabaseProbeResult;
import com.jobtracker.migrationpoc.database.LegacyDatabaseProbe;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/poc")
public class CompatibilityController {
    private final LegacyDatabaseProbe databaseProbe;

    public CompatibilityController(LegacyDatabaseProbe databaseProbe) {
        this.databaseProbe = databaseProbe;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
            "ok", true,
            "application", "job-tracker-migration-poc",
            "javaVersion", System.getProperty("java.version"),
            "databaseConfigured", databaseProbe.isConfigured(),
            "databaseProbeProtected", databaseProbe.isProtected(),
            "frontendBundled", new ClassPathResource("static/index.html").exists()
        );
    }

    @PostMapping("/database")
    public ResponseEntity<?> database(@RequestHeader(value = "X-POC-Token", required = false) String token) {
        if (!databaseProbe.isProtected()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", "服务器未配置 POC_ACCESS_TOKEN，数据库检查已禁用"));
        }
        if (!databaseProbe.isAuthorized(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "验证令牌不正确"));
        }
        DatabaseProbeResult result = databaseProbe.probe();
        return ResponseEntity.status(result.connected() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(result);
    }
}
