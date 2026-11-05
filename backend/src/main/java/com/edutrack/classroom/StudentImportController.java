package com.edutrack.classroom;

import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/students")
@PreAuthorize("hasRole('ADMIN_TEACHER')")
public class StudentImportController {
    private final StudentImportService importer;
    public StudentImportController(StudentImportService importer) {this.importer=importer;}
    @PostMapping(path="/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public StudentImportService.ImportResult importFile(@RequestParam("file") MultipartFile file,JwtAuthenticationToken principal) {
        return importer.importFile(file,UUID.fromString(principal.getName()));
    }
}
