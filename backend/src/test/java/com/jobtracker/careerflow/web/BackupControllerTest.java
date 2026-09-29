package com.jobtracker.careerflow.web;

import com.jobtracker.careerflow.database.BackupService;
import com.jobtracker.careerflow.database.BackupService.ImportResult;
import com.jobtracker.careerflow.database.BackupService.RestoreResult;
import com.jobtracker.careerflow.database.LegacyReadService.LegacyUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BackupControllerTest {
    @Test
    void restoresOnlyAfterExplicitConfirmationForTheAuthenticatedUser() throws Exception {
        AuthController auth = mock(AuthController.class);
        BackupService backups = mock(BackupService.class);
        LegacyUser user = new LegacyUser(7, "person@example.com", "salt", "hash", false);
        when(auth.authenticatedUser("token")).thenReturn(Optional.of(user));
        when(backups.restore("person@example.com", 12, "version-1"))
            .thenReturn(new RestoreResult(12, 10, 3, "version-2"));
        var controller = new BackupController(auth, backups);

        var response = controller.restore(
            "token", 12, new BackupController.RestoreRequest("version-1", "恢复"), sameOriginRequest()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new RestoreResult(12, 10, 3, "version-2"));
    }

    @Test
    void rejectsMissingConfirmationBeforeAuthenticationOrDatabaseAccess() {
        AuthController auth = mock(AuthController.class);
        BackupService backups = mock(BackupService.class);
        var controller = new BackupController(auth, backups);

        var response = controller.restore(
            "token", 12, new BackupController.RestoreRequest("version-1", "confirm"), sameOriginRequest()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(auth, backups);
    }

    @Test
    void importsOnlyAfterExplicitConfirmationForTheAuthenticatedUser() throws Exception {
        AuthController auth = mock(AuthController.class);
        BackupService backups = mock(BackupService.class);
        LegacyUser user = new LegacyUser(7, "person@example.com", "salt", "hash", false);
        var data = new ObjectMapper().readTree("{\"applications\":[],\"events\":[]}");
        when(auth.authenticatedUser("token")).thenReturn(Optional.of(user));
        when(backups.importDocument("person@example.com", data.toString(), "poc-import-data"))
            .thenReturn(new ImportResult(0, 0, "version-3"));
        var controller = new BackupController(auth, backups);

        var response = controller.importDocument(
            "token", new BackupController.ImportRequest(data, "导入"), sameOriginRequest()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new ImportResult(0, 0, "version-3"));
    }

    @Test
    void clearsOnlyAfterExplicitConfirmationForTheAuthenticatedUser() throws Exception {
        AuthController auth = mock(AuthController.class);
        BackupService backups = mock(BackupService.class);
        LegacyUser user = new LegacyUser(7, "person@example.com", "salt", "hash", false);
        when(auth.authenticatedUser("token")).thenReturn(Optional.of(user));
        when(backups.clearDocument("person@example.com")).thenReturn(new ImportResult(0, 0, "version-4"));
        var controller = new BackupController(auth, backups);

        var response = controller.clear(
            "token", new BackupController.ClearRequest("清空"), sameOriginRequest()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new ImportResult(0, 0, "version-4"));
    }

    private MockHttpServletRequest sameOriginRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.addHeader("Host", "demo.example.com");
        request.addHeader("Origin", "https://demo.example.com");
        return request;
    }
}
