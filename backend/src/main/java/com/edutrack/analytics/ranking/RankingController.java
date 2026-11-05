package com.edutrack.analytics.ranking;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RankingController {
    private final RankingService ranking;

    public RankingController(RankingService ranking) { this.ranking = ranking; }

    @GetMapping("/admin/classrooms/{classroomId}/ranking")
    @PreAuthorize("hasRole('ADMIN_TEACHER')")
    public RankingService.TeacherRanking teacher(@PathVariable UUID classroomId,
            @RequestParam(defaultValue = "ALL") RankingService.Period period,
            @RequestParam(required = false) UUID examId) {
        return ranking.teacher(classroomId, period, examId);
    }

    @GetMapping("/student/classrooms/{classroomId}/ranking")
    @PreAuthorize("hasRole('STUDENT')")
    public RankingService.PrivateRanking student(@PathVariable UUID classroomId,
            @RequestParam(defaultValue = "ALL") RankingService.Period period,
            @RequestParam(required = false) UUID examId,
            JwtAuthenticationToken principal) {
        return ranking.student(UUID.fromString(principal.getName()), classroomId, period, examId);
    }

    @GetMapping("/parent/children/{studentId}/classrooms/{classroomId}/ranking")
    @PreAuthorize("hasRole('PARENT')")
    public RankingService.PrivateRanking parent(@PathVariable UUID studentId, @PathVariable UUID classroomId,
            @RequestParam(defaultValue = "ALL") RankingService.Period period,
            @RequestParam(required = false) UUID examId,
            JwtAuthenticationToken principal) {
        return ranking.parent(UUID.fromString(principal.getName()), studentId, classroomId, period, examId);
    }
}
