package com.edutrack.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ParentLinkController {
    public record InviteRequest(@NotBlank @Email String parentEmail) {}
    public record AcceptRequest(@NotBlank String token) {}
    private final ParentLinkService links;
    public ParentLinkController(ParentLinkService links) { this.links=links; }

    @PostMapping("/admin/students/{studentId}/parent-links") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN_TEACHER')")
    public ParentLinkService.LinkView invite(@PathVariable UUID studentId,@Valid @RequestBody InviteRequest request,JwtAuthenticationToken principal) {
        return links.invite(studentId,request.parentEmail(),UUID.fromString(principal.getName()));
    }
    @GetMapping("/admin/students/{studentId}/parent-links") @PreAuthorize("hasRole('ADMIN_TEACHER')")
    public List<ParentLinkService.LinkView> links(@PathVariable UUID studentId) { return links.links(studentId); }
    @DeleteMapping("/admin/parent-links/{requestId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN_TEACHER')")
    public void revoke(@PathVariable UUID requestId) { links.revoke(requestId); }
    @PostMapping("/parent/links/accept") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PARENT')")
    public void accept(@Valid @RequestBody AcceptRequest request,JwtAuthenticationToken principal) {
        links.accept(UUID.fromString(principal.getName()),request.token());
    }
    @GetMapping("/parent/children") @PreAuthorize("hasRole('PARENT')")
    public List<ParentLinkService.ChildView> children(JwtAuthenticationToken principal) {
        return links.children(UUID.fromString(principal.getName()));
    }
}
