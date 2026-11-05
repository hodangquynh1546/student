package com.edutrack.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

class AuthCookieOriginTest {
    private final AuthService auth = mock(AuthService.class);
    private final AuthController controller = new AuthController(auth, mock(Environment.class), 7,
        "https://school.example,https://staff.example");

    @Test
    void rejectsAnotherOriginBeforeRevokingCookieSession() {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        request.addHeader("Origin", "https://other.example");

        var error = assertThrows(ResponseStatusException.class,
            () -> controller.logout("opaque-refresh-value", request, new MockHttpServletResponse()));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(auth);
    }

    @Test
    void rejectsBrowserRequestWithoutOriginUnlessSameOrigin() {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        request.addHeader("Sec-Fetch-Site", "same-site");

        var error = assertThrows(ResponseStatusException.class,
            () -> controller.logout("opaque-refresh-value", request, new MockHttpServletResponse()));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(auth);
    }

    @Test
    void rejectsMissingOriginAndFetchMetadata() {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        assertNull(request.getHeader("Origin"));
        assertNull(request.getHeader("Sec-Fetch-Site"));

        var error = assertThrows(ResponseStatusException.class,
            () -> controller.logout("opaque-refresh-value", request, new MockHttpServletResponse()));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(auth);
    }

    @Test
    void acceptsConfiguredOrigin() {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        request.addHeader("Origin", "https://staff.example");

        controller.logout("opaque-refresh-value", request, new MockHttpServletResponse());

        verify(auth).logout("opaque-refresh-value");
    }

    @Test
    void acceptsSameOriginFetchMetadata() {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        request.addHeader("Sec-Fetch-Site", "same-origin");

        controller.logout("opaque-refresh-value", request, new MockHttpServletResponse());

        verify(auth).logout("opaque-refresh-value");
    }
}
