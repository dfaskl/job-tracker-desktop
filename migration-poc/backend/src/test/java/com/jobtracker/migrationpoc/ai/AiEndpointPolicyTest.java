package com.jobtracker.migrationpoc.ai;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiEndpointPolicyTest {
    private final AiEndpointPolicy policy = new AiEndpointPolicy(new MockEnvironment());

    @Test
    void normalizesLegacyCompatibleApiRoots() {
        assertThat(policy.normalize("https://api.deepseek.com").toString())
            .isEqualTo("https://api.deepseek.com/chat/completions");
        assertThat(policy.normalize("https://api.openai.com/v1").toString())
            .isEqualTo("https://api.openai.com/v1/chat/completions");
        assertThat(policy.normalize("https://api.openai.com/v1/chat/completions").toString())
            .isEqualTo("https://api.openai.com/v1/chat/completions");
    }

    @Test
    void rejectsInsecureAndNonAllowlistedEndpointsBeforeConnecting() {
        assertThrows(AiEndpointPolicy.UnsafeEndpointException.class, () ->
            policy.endpoint("http://api.deepseek.com")
        );
        assertThrows(AiEndpointPolicy.UnsafeEndpointException.class, () ->
            policy.endpoint("https://untrusted.example")
        );
    }

    @Test
    void recognizesPrivateIpv4AndIpv6Addresses() throws Exception {
        assertThat(AiEndpointPolicy.isPrivateAddress(InetAddress.getByName("127.0.0.1"))).isTrue();
        assertThat(AiEndpointPolicy.isPrivateAddress(InetAddress.getByName("192.168.1.20"))).isTrue();
        assertThat(AiEndpointPolicy.isPrivateAddress(InetAddress.getByName("fc00::1"))).isTrue();
        assertThat(AiEndpointPolicy.isPrivateAddress(InetAddress.getByName("8.8.8.8"))).isFalse();
    }
}
