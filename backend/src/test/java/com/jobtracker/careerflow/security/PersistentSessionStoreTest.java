package com.jobtracker.careerflow.security;

import com.jobtracker.careerflow.database.ApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PersistentSessionStoreTest {
    @Test
    void usesPersistentSessionsByDefault() {
        ApplicationService sandbox = isolatedSandbox();
        PersistentSessionStore store = new PersistentSessionStore(new MockEnvironment(), sandbox);

        var status = store.status();

        assertThat(status.requested()).isTrue();
        assertThat(status.persistent()).isTrue();
        assertThat(status.sessionDays()).isEqualTo(7);
    }

    @Test
    void refusesPersistentModeUntilTheSandboxDatabaseIsEnabled() {
        ApplicationService sandbox = mock(ApplicationService.class);
        when(sandbox.status()).thenReturn(new ApplicationService.SandboxStatus(
            false, true, false, "业务数据库未配置"
        ));
        PersistentSessionStore store = new PersistentSessionStore(
            new MockEnvironment().withProperty("POC_PERSISTENT_SESSION_ENABLED", "true"), sandbox
        );

        assertThat(store.status().persistent()).isFalse();
        assertThat(store.status().message()).contains("未就绪");
    }

    @Test
    void supportsLegacySessionSettingsAndClampsTheTtl() {
        PersistentSessionStore store = new PersistentSessionStore(
            new MockEnvironment()
                .withProperty("POC_PERSISTENT_SESSION_ENABLED", "true")
                .withProperty("POC_SESSION_DAYS", "60"),
            isolatedSandbox()
        );

        assertThat(store.status().persistent()).isTrue();
        assertThat(store.status().databaseIsolated()).isTrue();
        assertThat(store.status().sessionDays()).isEqualTo(30);
    }

    @Test
    void hashesTokensExactlyLikeTheLegacyNodeService() {
        assertThat(PersistentSessionStore.tokenHash("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    private ApplicationService isolatedSandbox() {
        ApplicationService sandbox = mock(ApplicationService.class);
        when(sandbox.status()).thenReturn(new ApplicationService.SandboxStatus(
            true, true, true, "独立测试数据库写入已开启"
        ));
        return sandbox;
    }
}
