package com.jobtracker.migrationpoc.security;

import com.jobtracker.migrationpoc.database.ApplicationSandboxService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PocPersistentSessionStoreTest {
    @Test
    void staysInSignedCookieModeByDefault() {
        ApplicationSandboxService sandbox = isolatedSandbox();
        PocPersistentSessionStore store = new PocPersistentSessionStore(new MockEnvironment(), sandbox);

        var status = store.status();

        assertThat(status.requested()).isFalse();
        assertThat(status.persistent()).isFalse();
        assertThat(status.sessionDays()).isEqualTo(7);
    }

    @Test
    void refusesPersistentModeUntilTheSandboxDatabaseIsEnabled() {
        ApplicationSandboxService sandbox = mock(ApplicationSandboxService.class);
        when(sandbox.status()).thenReturn(new ApplicationSandboxService.SandboxStatus(
            false, true, false, "测试库与生产库地址相同"
        ));
        PocPersistentSessionStore store = new PocPersistentSessionStore(
            new MockEnvironment().withProperty("POC_PERSISTENT_SESSION_ENABLED", "true"), sandbox
        );

        assertThat(store.status().persistent()).isFalse();
        assertThat(store.status().message()).contains("未就绪");
    }

    @Test
    void enablesPersistentModeOnlyWithAnIsolatedSandboxAndClampsTheTtl() {
        PocPersistentSessionStore store = new PocPersistentSessionStore(
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
        assertThat(PocPersistentSessionStore.tokenHash("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    private ApplicationSandboxService isolatedSandbox() {
        ApplicationSandboxService sandbox = mock(ApplicationSandboxService.class);
        when(sandbox.status()).thenReturn(new ApplicationSandboxService.SandboxStatus(
            true, true, true, "独立测试数据库写入已开启"
        ));
        return sandbox;
    }
}
