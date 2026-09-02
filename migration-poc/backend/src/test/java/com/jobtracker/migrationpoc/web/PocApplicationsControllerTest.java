package com.jobtracker.migrationpoc.web;

import com.jobtracker.migrationpoc.database.LegacyReadService;
import com.jobtracker.migrationpoc.database.LegacyReadService.ApplicationPage;
import com.jobtracker.migrationpoc.database.LegacyReadService.ApplicationSummary;
import com.jobtracker.migrationpoc.database.LegacyReadService.LegacyUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PocApplicationsControllerTest {
    @Test
    void returnsOnlyTheAuthenticatedUsersApplicationPage() throws Exception {
        PocAuthController auth = mock(PocAuthController.class);
        LegacyReadService legacy = mock(LegacyReadService.class);
        LegacyUser user = new LegacyUser(9, "person@example.com", "salt", "hash", false);
        ApplicationSummary application = new ApplicationSummary(
            "app-1", "Example", "Engineer", "Shanghai", "Official", "2026-01-01", "Interview", "Pending", "2026-01-02"
        );
        when(auth.authenticatedUser("signed-token")).thenReturn(Optional.of(user));
        when(legacy.findApplications(9)).thenReturn(new ApplicationPage(List.of(application), 1, false));

        PocApplicationsController controller = new PocApplicationsController(auth, legacy);
        var response = controller.applications("signed-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PocApplicationsController.ApplicationsResponse body =
            (PocApplicationsController.ApplicationsResponse) response.getBody();
        assertThat(body.readOnly()).isTrue();
        assertThat(body.total()).isEqualTo(1);
        assertThat(body.applications()).containsExactly(application);
    }
}
