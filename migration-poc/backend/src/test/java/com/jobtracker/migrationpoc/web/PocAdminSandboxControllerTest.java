package com.jobtracker.migrationpoc.web;

import com.jobtracker.migrationpoc.database.AdminSandboxService;
import com.jobtracker.migrationpoc.database.AdminSandboxService.DisabledResult;
import com.jobtracker.migrationpoc.database.LegacyReadService.LegacyUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PocAdminSandboxControllerTest {
    @Test
    void disablesAUserOnlyForAnAuthenticatedSameOriginRequest() throws Exception {
        PocAuthController auth = mock(PocAuthController.class);
        AdminSandboxService service = mock(AdminSandboxService.class);
        LegacyUser admin = new LegacyUser(1, "admin@example.com", "salt", "hash", false);
        when(auth.authenticatedUser("token")).thenReturn(Optional.of(admin));
        when(service.setDisabled("admin@example.com", 9, true)).thenReturn(new DisabledResult(true, true));
        PocAdminSandboxController controller = new PocAdminSandboxController(auth, service);

        var response = controller.updateUser(
            "token", 9, new PocAdminSandboxController.UserStateRequest(true), sameOriginRequest()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new DisabledResult(true, true));
        verify(service).setDisabled("admin@example.com", 9, true);
    }

    @Test
    void rejectsCrossOriginMutationBeforeAuthenticationOrDatabaseAccess() {
        PocAuthController auth = mock(PocAuthController.class);
        AdminSandboxService service = mock(AdminSandboxService.class);
        PocAdminSandboxController controller = new PocAdminSandboxController(auth, service);
        MockHttpServletRequest request = sameOriginRequest();
        request.removeHeader("Origin");
        request.addHeader("Origin", "https://attacker.example.com");

        var response = controller.updateUser(
            "token", 9, new PocAdminSandboxController.UserStateRequest(true), request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(auth, service);
    }

    @Test
    void rejectsBlankDeleteConfirmationBeforeAuthenticationOrDatabaseAccess() {
        PocAuthController auth = mock(PocAuthController.class);
        AdminSandboxService service = mock(AdminSandboxService.class);
        PocAdminSandboxController controller = new PocAdminSandboxController(auth, service);

        var response = controller.deleteUser(
            "token", 9, new PocAdminSandboxController.DeleteUserRequest("  "), sameOriginRequest()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(auth, service);
    }

    private MockHttpServletRequest sameOriginRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.addHeader("Host", "demo.example.com");
        request.addHeader("Origin", "https://demo.example.com");
        return request;
    }
}
