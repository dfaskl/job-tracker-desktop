package com.jobtracker.migrationpoc.web;

import com.jobtracker.migrationpoc.application.ApplicationDocumentMutator.ApplicationView;
import com.jobtracker.migrationpoc.application.ApplicationDocumentMutator.Mutation;
import com.jobtracker.migrationpoc.database.ApplicationSandboxService;
import com.jobtracker.migrationpoc.database.LegacyReadService.LegacyUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PocApplicationSandboxControllerTest {
    @Test
    void createsOnlyForAnAuthenticatedUserInTheSandbox() throws Exception {
        PocAuthController auth = mock(PocAuthController.class);
        ApplicationSandboxService sandbox = mock(ApplicationSandboxService.class);
        LegacyUser user = new LegacyUser(9, "person@example.com", "salt", "hash", false);
        ApplicationView application = new ApplicationView(
            "app-1", "Example", "Engineer", "上海", "官网", "2026-09-03", "已投递", "等待结果",
            "备注", "2026-09-03 10:00", "2026-09-03 10:00"
        );
        when(auth.authenticatedUser("token")).thenReturn(Optional.of(user));
        when(sandbox.create(any(), any())).thenReturn(new Mutation("{}", application, 1));
        var controller = new PocApplicationSandboxController(auth, sandbox);

        var response = controller.create("token", requestBody(), sameOriginRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var body = (PocApplicationSandboxController.MutationResponse) response.getBody();
        assertThat(body.sandbox()).isTrue();
        assertThat(body.application()).isEqualTo(application);
    }

    @Test
    void rejectsCrossOriginWritesBeforeAuthenticationOrDatabaseAccess() {
        PocAuthController auth = mock(PocAuthController.class);
        ApplicationSandboxService sandbox = mock(ApplicationSandboxService.class);
        var controller = new PocApplicationSandboxController(auth, sandbox);
        MockHttpServletRequest request = sameOriginRequest();
        request.removeHeader("Origin");
        request.addHeader("Origin", "https://invalid.example");

        var response = controller.create("token", requestBody(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(auth, sandbox);
    }

    private PocApplicationSandboxController.ApplicationWriteRequest requestBody() {
        return new PocApplicationSandboxController.ApplicationWriteRequest(
            "Example", "Engineer", "上海", "官网", "2026-09-03", "已投递", "等待结果", "备注", ""
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
