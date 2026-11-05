package com.edutrack.analytics;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AnalyticsController {
    private final AnalyticsService analytics;
    public AnalyticsController(AnalyticsService analytics) { this.analytics = analytics; }

    @GetMapping("/admin/analytics/dashboard")
    @PreAuthorize("hasRole('ADMIN_TEACHER')")
    public AnalyticsService.AdminDashboard admin() { return analytics.admin(); }

    @GetMapping("/student/analytics/dashboard")
    @PreAuthorize("hasRole('STUDENT')")
    public AnalyticsService.LearnerDashboard student(JwtAuthenticationToken principal) {
        return analytics.student(UUID.fromString(principal.getName()));
    }

    @GetMapping("/parent/children/{studentId}/analytics/dashboard")
    @PreAuthorize("hasRole('PARENT')")
    public AnalyticsService.LearnerDashboard parent(@PathVariable UUID studentId,
                                                    JwtAuthenticationToken principal) {
        return analytics.parent(UUID.fromString(principal.getName()), studentId);
    }
}
