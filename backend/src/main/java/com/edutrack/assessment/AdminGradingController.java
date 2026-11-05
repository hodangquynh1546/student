package com.edutrack.assessment;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/grading")
@PreAuthorize("hasRole('ADMIN_TEACHER')")
public class AdminGradingController {
    private final AdminGradingService grading;
    public AdminGradingController(AdminGradingService grading) {this.grading=grading;}
    @GetMapping("/queue")
    public List<AdminGradingService.QueueItem> queue() {return grading.queue();}
    @GetMapping("/attempts/{id}")
    public AdminGradingService.Detail detail(@PathVariable UUID id) {return grading.detail(id);}
    @PutMapping("/attempts/{id}/questions/{questionId}")
    public AdminGradingService.Detail grade(@PathVariable UUID id,@PathVariable UUID questionId,
                                             @RequestBody AdminGradingService.GradeInput input,JwtAuthenticationToken principal) {
        return grading.grade(id,questionId,input,UUID.fromString(principal.getName()));
    }
    @PostMapping("/attempts/{id}/publish")
    public AdminGradingService.Detail publish(@PathVariable UUID id) {return grading.publish(id);}
    @PostMapping("/attempts/{id}/questions/{questionId}/ai-suggestion")
    public AdminGradingService.AiSuggestion suggest(@PathVariable UUID id,@PathVariable UUID questionId) {
        return grading.suggestEssay(id,questionId);
    }
    @PostMapping("/attempts/{id}/questions/{questionId}/ai-evaluations")
    public AdminGradingService.AiEvaluation evaluate(@PathVariable UUID id,@PathVariable UUID questionId) {
        return grading.evaluateEssay(id,questionId);
    }
    @GetMapping("/attempts/{id}/questions/{questionId}/ai-evaluations")
    public List<AdminGradingService.AiEvaluation> evaluations(@PathVariable UUID id,@PathVariable UUID questionId) {
        return grading.evaluations(id,questionId);
    }
}
