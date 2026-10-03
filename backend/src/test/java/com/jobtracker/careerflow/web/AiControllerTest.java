package com.jobtracker.careerflow.web;

import com.jobtracker.careerflow.database.AiService;
import com.jobtracker.careerflow.database.LegacyReadService.LegacyUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiControllerTest {
    @Test
    void revealsOnlyTheAuthenticatedUsersKeyWithoutCaching() throws Exception {
        AuthController auth = mock(AuthController.class);
        AiService service = mock(AiService.class);
        when(auth.authenticatedUser("session")).thenReturn(Optional.of(
            new LegacyUser(7, "owner@example.com", "", "", false)
        ));
        when(service.revealApiKey("owner@example.com")).thenReturn("secret-value");

        var response = new AiController(auth, service).revealConfig("session", sameOriginRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(response.getBody()).isEqualTo(Map.of("apiKey", "secret-value"));
        verify(service).revealApiKey("owner@example.com");
    }

    @Test
    void rejectsUnauthenticatedAndCrossOriginRevealRequests() throws Exception {
        AuthController auth = mock(AuthController.class);
        AiService service = mock(AiService.class);
        when(auth.authenticatedUser("missing")).thenReturn(Optional.empty());
        AiController controller = new AiController(auth, service);

        assertThat(controller.revealConfig("missing", sameOriginRequest()).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        MockHttpServletRequest crossOrigin = new MockHttpServletRequest();
        crossOrigin.setScheme("https");
        crossOrigin.addHeader("Host", "app.example.com");
        crossOrigin.addHeader("Origin", "https://other.example.com");
        assertThat(controller.revealConfig("session", crossOrigin).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(service);
    }

    private MockHttpServletRequest sameOriginRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.addHeader("Host", "app.example.com");
        request.addHeader("Origin", "https://app.example.com");
        return request;
    }
}
