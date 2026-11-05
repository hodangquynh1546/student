package com.edutrack.security;

import com.edutrack.identity.AccountStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ActiveAccountFilter extends OncePerRequestFilter {
    private final JdbcTemplate db;
    public ActiveAccountFilter(JdbcTemplate db) { this.db=db; }
    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
        throws ServletException,IOException {
        var principal=SecurityContextHolder.getContext().getAuthentication();
        if(principal instanceof JwtAuthenticationToken jwt) {
            try {
                UUID id=UUID.fromString(jwt.getName());
                String role=jwt.getToken().getClaimAsString("role");
                var active=db.query("SELECT role,status FROM users WHERE id=?",
                    (rs,row)->role.equals(rs.getString("role")) && AccountStatus.ACTIVE.name().equals(rs.getString("status")),id);
                if(active.size()!=1 || !active.getFirst()) { response.sendError(HttpServletResponse.SC_UNAUTHORIZED);return; }
            } catch(IllegalArgumentException | NullPointerException e) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);return;
            }
        }
        chain.doFilter(request,response);
    }
}
