package com.jobtracker.careerflow.web;

import com.jobtracker.careerflow.database.EventService;
import com.jobtracker.careerflow.database.EventService.SharedEvent;
import com.jobtracker.careerflow.database.EventService.SharedTimelines;
import com.jobtracker.careerflow.database.EventService.UserTimeline;
import com.jobtracker.careerflow.database.LegacyReadService.LegacyUser;
import com.jobtracker.careerflow.event.EventDocumentMutator.EventView;
import com.jobtracker.careerflow.event.EventDocumentMutator.Mutation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EventControllerTest {
    @Test
    void returnsSharedTimelinesForAuthenticatedUsers() throws Exception {
        AuthController auth = mock(AuthController.class);
        EventService sandbox = mock(EventService.class);
        LegacyUser user = new LegacyUser(7, "person@example.com", "salt", "hash", false);
        var users = List.of(new UserTimeline("person@example.com", "小明", List.of(
            new SharedEvent("evt-1", "面试", "一面", "2026-09-21 10:00", "", "Example")
        )));
        var timelines = new SharedTimelines("3", "第一面试室", users);
        when(auth.authenticatedUser("token")).thenReturn(Optional.of(user));
        when(sandbox.findTimelines("person@example.com")).thenReturn(timelines);
        var controller = new EventController(auth, sandbox);

        var response = controller.timelines("token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = (SharedTimelines) response.getBody();
        assertThat(body.users()).isEqualTo(users);
        assertThat(body.groupName()).isEqualTo("第一面试室");
    }

    @Test
    void createsAnEventOnlyForTheAuthenticatedSandboxUser() throws Exception {
        AuthController auth = mock(AuthController.class);
        EventService sandbox = mock(EventService.class);
        LegacyUser user = new LegacyUser(7, "person@example.com", "salt", "hash", false);
        EventView event = event();
        when(auth.authenticatedUser("token")).thenReturn(Optional.of(user));
        when(sandbox.create(any(), any())).thenReturn(new Mutation("{}", event, 1));
        var controller = new EventController(auth, sandbox);

        var response = controller.create("token", requestBody(), sameOriginRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var body = (EventController.MutationResponse) response.getBody();
        assertThat(body.sandbox()).isTrue();
        assertThat(body.event()).isEqualTo(event);
    }

    @Test
    void rejectsCrossOriginEventWritesBeforeAuthentication() {
        AuthController auth = mock(AuthController.class);
        EventService sandbox = mock(EventService.class);
        var controller = new EventController(auth, sandbox);
        MockHttpServletRequest request = sameOriginRequest();
        request.removeHeader("Origin");
        request.addHeader("Origin", "https://invalid.example");

        var response = controller.create("token", requestBody(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(auth, sandbox);
    }

    private EventController.EventWriteRequest requestBody() {
        return new EventController.EventWriteRequest(
            "app-1", "面试", "一面", "2026-09-05 09:00", "2026-09-05 10:00", "线上", "备注", ""
        );
    }

    private EventView event() {
        return new EventView(
            "evt-1", "app-1", "面试", "一面", "2026-09-05 09:00", "2026-09-05 10:00", "线上",
            "备注", "Example", "Engineer", false, false, false, "", "2026-09-03 06:30", "2026-09-03 06:30",
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
