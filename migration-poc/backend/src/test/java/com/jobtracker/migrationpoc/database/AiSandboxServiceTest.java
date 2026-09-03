package com.jobtracker.migrationpoc.database;

import com.jobtracker.migrationpoc.ai.AiEndpointPolicy;
import com.jobtracker.migrationpoc.compat.LegacySecretCrypto;
import com.jobtracker.migrationpoc.compat.LegacySecretCryptoWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiSandboxServiceTest {
    @Test
    void keepsExternalCallsDisabledWithoutEveryExplicitGate() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("POC_ENCRYPTION_KEY", "0123456789abcdef0123456789abcdef")
            .withProperty("POC_AI_CALLS_ENABLED", "true");
        ApplicationSandboxService sandbox = mock(ApplicationSandboxService.class);
        when(sandbox.status()).thenReturn(new ApplicationSandboxService.SandboxStatus(
            false, false, false, "未开启"
        ));

        var status = service(environment, sandbox).status();

        assertThat(status.callsEnabled()).isFalse();
        assertThat(status.sandboxEnabled()).isFalse();
    }

    @Test
    void parsesJsonInsideTheCommonMarkdownEnvelope() throws Exception {
        ApplicationSandboxService sandbox = mock(ApplicationSandboxService.class);
        when(sandbox.status()).thenReturn(new ApplicationSandboxService.SandboxStatus(
            true, true, true, "已开启"
        ));
        AiSandboxService service = service(new MockEnvironment(), sandbox);

        var parsed = service.parseModelJson("```json\n{\"company\":\"Example\",\"summary\":\"\"}\n```");

        assertThat(parsed.path("company").asText()).isEqualTo("Example");
    }

    private AiSandboxService service(MockEnvironment environment, ApplicationSandboxService sandbox) {
        ObjectMapper mapper = new ObjectMapper();
        LegacySecretCrypto crypto = new LegacySecretCrypto();
        return new AiSandboxService(
            environment,
            sandbox,
            crypto,
            new LegacySecretCryptoWriter(crypto),
            new AiEndpointPolicy(environment),
            mapper
        );
    }
}
