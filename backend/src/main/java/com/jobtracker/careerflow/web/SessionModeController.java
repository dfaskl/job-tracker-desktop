package com.jobtracker.careerflow.web;

import com.jobtracker.careerflow.security.PersistentSessionStore;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/poc/session-mode")
public class SessionModeController {
    private final PersistentSessionStore persistentSessionStore;

    public SessionModeController(PersistentSessionStore persistentSessionStore) {
        this.persistentSessionStore = persistentSessionStore;
    }

    @GetMapping
    public ResponseEntity<?> status() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(persistentSessionStore.status());
    }
}
