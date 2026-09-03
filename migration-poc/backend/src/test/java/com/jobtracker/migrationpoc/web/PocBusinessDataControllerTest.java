package com.jobtracker.migrationpoc.web;

import com.jobtracker.migrationpoc.database.LegacyReadService;
import com.jobtracker.migrationpoc.database.LegacyReadService.LegacyUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PocBusinessDataControllerTest {
    @Test
    void returnsTheAuthenticatedUsersCompleteReadOnlyDocument() throws Exception {
        PocAuthController auth = mock(PocAuthController.class);
        LegacyReadService legacy = mock(LegacyReadService.class);
        LegacyUser user = new LegacyUser(9, "person@example.com", "salt", "hash", false);
        var data = new ObjectMapper().readTree("{\"applications\":[],\"events\":[]}");
        when(auth.authenticatedUser("signed-token")).thenReturn(Optional.of(user));
        when(legacy.findBusinessData(9)).thenReturn(Optional.of(data));

        var response = new PocBusinessDataController(auth, legacy).data("signed-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = (PocBusinessDataController.BusinessDataResponse) response.getBody();
        assertThat(body.readOnly()).isTrue();
        assertThat(body.exists()).isTrue();
        assertThat(body.data()).isEqualTo(data);
    }
}
