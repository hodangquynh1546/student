package com.edutrack.assessment;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/parent/children/{studentId}/results")
@PreAuthorize("hasRole('PARENT')")
public class ParentResultController {
    private final ParentResultService results;
    public ParentResultController(ParentResultService results) { this.results = results; }

    @GetMapping
    public ParentResultService.ResultPage list(@PathVariable UUID studentId, @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size, JwtAuthenticationToken principal) {
        return results.list(UUID.fromString(principal.getName()), studentId, page, size);
    }

    @GetMapping("/{attemptId}")
    public ParentResultService.ResultDetail detail(@PathVariable UUID studentId, @PathVariable UUID attemptId,
                                                    JwtAuthenticationToken principal) {
        return results.detail(UUID.fromString(principal.getName()), studentId, attemptId);
    }
}
