package com.jobtracker.migrationpoc.web;

import com.jobtracker.migrationpoc.database.EventSandboxService;
import com.jobtracker.migrationpoc.database.LegacyReadService.LegacyUser;
import com.jobtracker.migrationpoc.event.EventDocumentMutator.EventView;
import com.jobtracker.migrationpoc.event.EventDocumentMutator.Mutation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PocEventSandboxControllerTest {
    @Test
    void createsAnEventOnlyForTheAuthenticatedSandboxUser() throws Exception {
        PocAuthController auth = mock(PocAuthController.class);
        EventSandboxService sandbox = mock(EventSandboxService.class);
        LegacyUser user = new LegacyUser(7, "person@example.com", "salt", "hash", false);
        EventView event = event();
        when(auth.authenticatedUser("token")).thenReturn(Optional.of(user));
        when(sandbox.create(any(), any())).thenReturn(new Mutation("{}", event, 1));
        var controller = new PocEventSandboxController(auth, sandbox);

        var response = controller.create("token", requestBody(), sameOriginRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var body = (PocEventSandboxController.MutationResponse) response.getBody();
        assertThat(body.sandbox()).isTrue();
        assertThat(body.event()).isEqualTo(event);
    }

    @Test
    void rejectsCrossOriginEventWritesBeforeAuthentication() {
        PocAuthController auth = mock(PocAuthController.class);
        EventSandboxService sandbox = mock(EventSandboxService.class);
        var controller = new PocEventSandboxController(auth, sandbox);
        MockHttpServletRequest request = sameOriginRequest();
        request.removeHeader("Origin");
        request.addHeader("Origin", "https://invalid.example");

        var response = controller.create("token", requestBody(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(auth, sandbox);
    }

    private PocEventSandboxController.EventWriteRequest requestBody() {
        return new PocEventSandboxController.EventWriteRequest(
            "app-1", "面试", "一面", "2026-09-05 09:00", "2026-09-05 10:00", "线上", "备注", ""
        );
    }

    private EventView event() {
        return new EventView(
            "evt-1", "app-1", "面试", "一面", "2026-09-05 09:00", "2026-09-05 10:00", "线上",
            "备注", "Example", "Engineer", false, false, "", "2026-09-03 06:30", "2026-09-03 06:30",
            "2026-09-05 09:00"
        );
    }

    private MockHttpServletRequest sameOriginRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.addHeader("Host", "demo.example.com");
        request.addHeader("Origin", "https://demo.example.com");
        return request;
    }
}
