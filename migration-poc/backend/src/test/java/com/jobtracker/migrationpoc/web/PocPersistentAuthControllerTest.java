package com.jobtracker.migrationpoc.web;

import com.jobtracker.migrationpoc.compat.LegacyPasswordVerifier;
import com.jobtracker.migrationpoc.database.LegacyReadService;
import com.jobtracker.migrationpoc.database.LegacyReadService.LegacyUser;
import com.jobtracker.migrationpoc.security.PocPersistentSessionStore;
import com.jobtracker.migrationpoc.security.PocSessionManager;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PocPersistentAuthControllerTest {
    private static final String SALT = "00112233445566778899aabbccddeeff";
    private static final String HASH = "90f58584d0d9caefd484464d32143aed9f18b8a847d54ada8e0f2137b0980406"
        + "d7884c61070d513109e4ccb64cf432974cd0ccb27c850c1f7e3b610218cafb78";

    @Test
    void issuesVerifiesAndRevokesAPersistentDatabaseSession() throws Exception {
        LegacyReadService legacy = mock(LegacyReadService.class);
        LegacyUser user = new LegacyUser(7, "person@example.com", SALT, HASH, false);
        when(legacy.isConfigured()).thenReturn(true);
        when(legacy.findUserByEmail("person@example.com")).thenReturn(Optional.of(user));
        PocPersistentSessionStore persistent = mock(PocPersistentSessionStore.class);
        when(persistent.isEnabled()).thenReturn(true);
        when(persistent.issue("person@example.com")).thenReturn("database-token");
        when(persistent.sessionTtl()).thenReturn(Duration.ofDays(7));
        when(persistent.verifyEmail("database-token")).thenReturn(Optional.of("person@example.com"));
        PocSessionManager signed = new PocSessionManager(new MockEnvironment());
        PocAuthController controller = new PocAuthController(
            legacy, new LegacyPasswordVerifier(), signed, persistent
        );
        MockHttpServletRequest request = secureRequest();

        var login = controller.login(
            new PocAuthController.LoginRequest("person@example.com", "migration-poc-password"), request
        );

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
            .contains("poc_session=database-token", "Max-Age=604800", "HttpOnly", "Secure");
        assertThat(controller.session("database-token").getStatusCode()).isEqualTo(HttpStatus.OK);

        var logout = controller.logout("database-token", request);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(persistent).revoke("database-token");
    }

    private MockHttpServletRequest secureRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.addHeader("Host", "demo.example.com");
        request.addHeader("Origin", "https://demo.example.com");
        return request;
    }
}
