package com.edutrack.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

class SensitiveEndpointRateLimitFilterTest {
    @Test
    void limitsLoginByClientAddressAndReturnsRetryAfter() throws Exception {
        var filter = new SensitiveEndpointRateLimitFilter(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        for (int i = 0; i < 10; i++) {
            var response = new MockHttpServletResponse();
            filter.doFilter(request("192.0.2.1"), response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
        var blocked = new MockHttpServletResponse();
        filter.doFilter(request("192.0.2.1"), blocked, new MockFilterChain());
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("900");

        var anotherClient = new MockHttpServletResponse();
        filter.doFilter(request("192.0.2.2"), anotherClient, new MockFilterChain());
        assertThat(anotherClient.getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest request(String address) {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(address);
        return request;
    }
}
