package com.edutrack.file;

import com.edutrack.assessment.ParentResultService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class AttachmentController {
    private final AttachmentService attachments;
    private final ParentResultService parentResults;
    public AttachmentController(AttachmentService attachments,ParentResultService parentResults) {
        this.attachments=attachments;this.parentResults=parentResults;
    }

    @PostMapping(path="/student/attempts/{attemptId}/questions/{questionId}/attachments",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<AttachmentService.Attachment> upload(@PathVariable UUID attemptId,@PathVariable UUID questionId,
                                                                 @RequestParam("file") MultipartFile file,JwtAuthenticationToken principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attachments.upload(attemptId,questionId,user(principal),file));
    }

    @GetMapping("/student/attempts/{attemptId}/questions/{questionId}/attachments")
    @PreAuthorize("hasRole('STUDENT')")
    public List<AttachmentService.Attachment> studentList(@PathVariable UUID attemptId,@PathVariable UUID questionId,
                                                           JwtAuthenticationToken principal) {
        return attachments.list(attemptId,questionId,user(principal),"STUDENT");
    }

    @GetMapping("/admin/grading/attempts/{attemptId}/questions/{questionId}/attachments")
    @PreAuthorize("hasRole('ADMIN_TEACHER')")
    public List<AttachmentService.Attachment> adminList(@PathVariable UUID attemptId,@PathVariable UUID questionId,
                                                         JwtAuthenticationToken principal) {
        return attachments.list(attemptId,questionId,user(principal),"ADMIN_TEACHER");
    }

    @GetMapping("/parent/children/{studentId}/results/{attemptId}/questions/{questionId}/attachments")
    @PreAuthorize("hasRole('PARENT')")
    public List<AttachmentService.Attachment> parentList(@PathVariable UUID studentId,@PathVariable UUID attemptId,
                                                          @PathVariable UUID questionId,JwtAuthenticationToken principal) {
        parentResults.detail(user(principal),studentId,attemptId);
        return attachments.list(attemptId,questionId,user(principal),"PARENT");
    }

    @GetMapping("/files/{fileId}/content")
    @PreAuthorize("hasAnyRole('ADMIN_TEACHER','STUDENT','PARENT')")
    public ResponseEntity<byte[]> content(@PathVariable UUID fileId,JwtAuthenticationToken principal) {
        var content=attachments.download(fileId,user(principal),principal.getToken().getClaimAsString("role"));
        boolean inline=content.mimeType().startsWith("image/");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.mimeType()))
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION,(inline?"inline":"attachment")+"; filename=\""+fileId+(inline?".image":".pdf")+"\"")
            .body(content.bytes());
    }
    private static UUID user(JwtAuthenticationToken principal) { return UUID.fromString(principal.getName()); }
}
