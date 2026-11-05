package com.edutrack.ai;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiLearningController {
    private final AiLearningService service;
    public AiLearningController(AiLearningService service) {this.service=service;}
    @PostMapping("/student/ask") @PreAuthorize("hasRole('STUDENT')")
    public AiLearningService.AskOutput ask(@RequestBody AiLearningService.AskInput input,JwtAuthenticationToken principal) {
        return service.ask(UUID.fromString(principal.getName()),input);
    }
    @PostMapping("/admin/questions/generate") @PreAuthorize("hasRole('ADMIN_TEACHER')")
    public AiLearningService.GenerateOutput generate(@RequestBody AiLearningService.GenerateInput input,JwtAuthenticationToken principal) {
        return service.generate(UUID.fromString(principal.getName()),input);
    }
}
