package com.jobtracker.migrationpoc.web;

import com.jobtracker.migrationpoc.security.PocPersistentSessionStore;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/poc/session-mode")
public class PocSessionModeController {
    private final PocPersistentSessionStore persistentSessionStore;

    public PocSessionModeController(PocPersistentSessionStore persistentSessionStore) {
        this.persistentSessionStore = persistentSessionStore;
    }

    @GetMapping
    public ResponseEntity<?> status() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(persistentSessionStore.status());
    }
}
