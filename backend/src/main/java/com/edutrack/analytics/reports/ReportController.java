package com.edutrack.analytics.reports;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReportController {
    private final ReportService reports;
    private final ReportExportService exports;
    public ReportController(ReportService reports, ReportExportService exports) {
        this.reports = reports;
        this.exports = exports;
    }

    @GetMapping("/admin/students/{studentId}/reports")
    @PreAuthorize("hasRole('ADMIN_TEACHER')")
    public List<ReportService.Report> admin(@PathVariable UUID studentId) {
        return reports.adminList(studentId);
    }

    @PostMapping("/admin/students/{studentId}/reports")
    @PreAuthorize("hasRole('ADMIN_TEACHER')")
    public ReportService.Report generate(@PathVariable UUID studentId,
                                         @RequestParam ReportService.Period period,
                                         @RequestParam LocalDate start) {
        return reports.generate(studentId, period, start);
    }

    @GetMapping("/student/reports")
    @PreAuthorize("hasRole('STUDENT')")
    public List<ReportService.Report> student(JwtAuthenticationToken principal) {
        return reports.studentList(UUID.fromString(principal.getName()));
    }

    @GetMapping("/parent/children/{studentId}/reports")
    @PreAuthorize("hasRole('PARENT')")
    public List<ReportService.Report> parent(@PathVariable UUID studentId, JwtAuthenticationToken principal) {
        return reports.parentList(UUID.fromString(principal.getName()), studentId);
    }

    @GetMapping("/admin/students/{studentId}/reports/{reportId}/export")
    @PreAuthorize("hasRole('ADMIN_TEACHER')")
    public ResponseEntity<byte[]> exportAdmin(@PathVariable UUID studentId, @PathVariable UUID reportId,
                                               @RequestParam ReportExportService.Format format) {
        return download(reports.adminReport(studentId, reportId), format);
    }

    @GetMapping("/student/reports/{reportId}/export")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<byte[]> exportStudent(@PathVariable UUID reportId,
                                                 @RequestParam ReportExportService.Format format,
                                                 JwtAuthenticationToken principal) {
        return download(reports.studentReport(UUID.fromString(principal.getName()), reportId), format);
    }

    @GetMapping("/parent/children/{studentId}/reports/{reportId}/export")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<byte[]> exportParent(@PathVariable UUID studentId, @PathVariable UUID reportId,
                                                @RequestParam ReportExportService.Format format,
                                                JwtAuthenticationToken principal) {
        return download(reports.parentReport(UUID.fromString(principal.getName()), studentId, reportId), format);
    }

    private ResponseEntity<byte[]> download(ReportService.Report report, ReportExportService.Format format) {
        MediaType type = format == ReportExportService.Format.PDF ? MediaType.APPLICATION_PDF
            : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok().contentType(type)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exports.filename(report, format) + "\"")
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .body(exports.export(report, format));
    }
}
