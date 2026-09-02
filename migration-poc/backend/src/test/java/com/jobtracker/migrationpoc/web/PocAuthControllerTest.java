package com.jobtracker.migrationpoc.web;

import com.jobtracker.migrationpoc.compat.LegacyPasswordVerifier;
import com.jobtracker.migrationpoc.database.LegacyReadService;
import com.jobtracker.migrationpoc.database.LegacyReadService.LegacyUser;
import com.jobtracker.migrationpoc.security.PocSessionManager;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PocAuthControllerTest {
    private static final String SALT = "00112233445566778899aabbccddeeff";
    private static final String HASH = "90f58584d0d9caefd484464d32143aed9f18b8a847d54ada8e0f2137b0980406"
        + "d7884c61070d513109e4ccb64cf432974cd0ccb27c850c1f7e3b610218cafb78";

    @Test
    void logsInWithALegacyPasswordAndRestoresTheSignedSession() throws Exception {
        LegacyReadService legacy = mock(LegacyReadService.class);
        LegacyUser user = new LegacyUser(7, "person@example.com", SALT, HASH, false);
        when(legacy.isConfigured()).thenReturn(true);
        when(legacy.findUserByEmail("person@example.com")).thenReturn(Optional.of(user));
        when(legacy.findUserById(7)).thenReturn(Optional.of(user));

        PocSessionManager sessions = new PocSessionManager(new MockEnvironment()
            .withProperty("POC_SESSION_SECRET", "0123456789abcdef0123456789abcdef"));
        PocAuthController controller = new PocAuthController(legacy, new LegacyPasswordVerifier(), sessions);
        MockHttpServletRequest request = secureRequest();

        var response = controller.login(
            new PocAuthController.LoginRequest("Person@Example.com", "migration-poc-password"),
            request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).contains("HttpOnly", "Secure", "SameSite=Strict");
        String token = cookie.substring("poc_session=".length(), cookie.indexOf(';'));
        assertThat(controller.session(token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void rejectsCrossOriginLoginBeforeReadingCredentials() {
        LegacyReadService legacy = mock(LegacyReadService.class);
        PocSessionManager sessions = new PocSessionManager(new MockEnvironment()
            .withProperty("POC_SESSION_SECRET", "0123456789abcdef0123456789abcdef"));
        PocAuthController controller = new PocAuthController(legacy, new LegacyPasswordVerifier(), sessions);
        MockHttpServletRequest request = secureRequest();
        request.removeHeader("Origin");
        request.addHeader("Origin", "https://invalid.example");

        var response = controller.login(
            new PocAuthController.LoginRequest("person@example.com", "migration-poc-password"),
            request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private MockHttpServletRequest secureRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.addHeader("Host", "demo.example.com");
        request.addHeader("Origin", "https://demo.example.com");
        return request;
    }
}
