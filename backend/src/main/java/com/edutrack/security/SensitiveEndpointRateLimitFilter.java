package com.edutrack.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Per-process IP limit for public credential endpoints. Use a shared store when scaling horizontally. */
@Component
public class SensitiveEndpointRateLimitFilter extends OncePerRequestFilter {
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public SensitiveEndpointRateLimitFilter() { this(Clock.systemUTC()); }
    SensitiveEndpointRateLimitFilter(Clock clock) { this.clock = clock; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!"POST".equals(request.getMethod()) || !path.startsWith("/api/v1/auth/")) {
            chain.doFilter(request, response);
            return;
        }
        Policy policy = switch (path.substring("/api/v1/auth/".length())) {
            case "login" -> new Policy(10, Duration.ofMinutes(15));
            case "forgot-password", "reset-password", "activate" -> new Policy(5, Duration.ofHours(1));
            case "refresh" -> new Policy(30, Duration.ofMinutes(15));
            default -> null;
        };
        if (policy == null) {
            chain.doFilter(request, response);
            return;
        }
        Instant now = clock.instant();
        String key = path + ":" + request.getRemoteAddr();
        Window window = windows.compute(key, (ignored, existing) -> {
            if (existing == null || !now.isBefore(existing.started().plus(policy.duration())))
                return new Window(now, 1);
            return new Window(existing.started(), existing.count() + 1);
        });
        if (window.count() > policy.maxRequests()) {
            long seconds = Math.max(1, Duration.between(now, window.started().plus(policy.duration())).toSeconds());
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(seconds));
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}");
            return;
        }
        if (windows.size() > 10_000) windows.entrySet().removeIf(e -> !now.isBefore(e.getValue().started().plus(Duration.ofHours(1))));
        chain.doFilter(request, response);
    }

    private record Policy(int maxRequests, Duration duration) { }
    private record Window(Instant started, int count) { }
}
