package com.edutrack.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import jakarta.servlet.FilterChain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ActiveAccountFilterTest {
    private final JdbcTemplate db=mock(JdbcTemplate.class);
    private final ActiveAccountFilter filter=new ActiveAccountFilter(db);
    private final FilterChain chain=mock(FilterChain.class);

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    private void authenticate(UUID id) {
        var jwt=Jwt.withTokenValue("bearer").header("alg","HS256").subject(id.toString())
            .claim("role","STUDENT").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test void previouslyIssuedTokenIsRejectedAfterAccountIsLocked() throws Exception {
        UUID id=UUID.randomUUID(); authenticate(id);
        when(db.query(anyString(),any(RowMapper.class),eq(id))).thenReturn(List.of(false));
        var response=new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET","/api/v1/student/classrooms"),response,chain);

        assertEquals(401,response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test void activeAccountWithMatchingRoleCanPass() throws Exception {
        UUID id=UUID.randomUUID(); authenticate(id);
        when(db.query(anyString(),any(RowMapper.class),eq(id))).thenReturn(List.of(true));
        var response=new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET","/api/v1/student/classrooms"),response,chain);

        assertEquals(200,response.getStatus());
        verify(chain).doFilter(any(),eq(response));
    }
}
