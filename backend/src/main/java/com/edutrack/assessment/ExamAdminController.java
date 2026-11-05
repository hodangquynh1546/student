package com.edutrack.assessment;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/exams")
@PreAuthorize("hasRole('ADMIN_TEACHER')")
public class ExamAdminController {
    private final ExamAdminService exams;
    public ExamAdminController(ExamAdminService exams) { this.exams=exams; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ExamAdminService.ExamView create(@RequestBody ExamAdminService.Input input,JwtAuthenticationToken principal) {
        return exams.create(input,UUID.fromString(principal.getName()));
    }
    @GetMapping public List<ExamAdminService.ExamView> list() { return exams.list(); }
    @GetMapping("/{id}") public ExamAdminService.Detail get(@PathVariable UUID id) { return exams.detail(id); }
    @PutMapping("/{id}")
    public ExamAdminService.ExamView update(@PathVariable UUID id,@RequestParam long version,
                                            @RequestBody ExamAdminService.Input input) {
        return exams.update(id,input,version);
    }
    @PostMapping("/{id}/questions") @ResponseStatus(HttpStatus.CREATED)
    public ExamAdminService.Detail addQuestion(@PathVariable UUID id,@RequestBody ExamAdminService.AddQuestion input) {
        return exams.addQuestion(id,input);
    }
    @PostMapping("/{id}/inline-questions") @ResponseStatus(HttpStatus.CREATED)
    public ExamAdminService.Detail addInlineQuestion(@PathVariable UUID id,@RequestBody ExamAdminService.InlineQuestion input,
                                                      JwtAuthenticationToken principal) {
        return exams.addInlineQuestion(id,input,UUID.fromString(principal.getName()));
    }
    @PutMapping("/{id}/questions/order")
    public ExamAdminService.Detail reorderQuestions(@PathVariable UUID id,@RequestBody ExamAdminService.QuestionOrder input) {
        return exams.reorderQuestions(id,input);
    }
    @PatchMapping("/{id}/questions/{questionId}/points")
    public ExamAdminService.Detail changePoints(@PathVariable UUID id,@PathVariable UUID questionId,
                                                 @RequestBody ExamAdminService.PointsInput input) {
        return exams.changePoints(id,questionId,input);
    }
    @DeleteMapping("/{id}/questions/{questionId}")
    public ExamAdminService.Detail removeQuestion(@PathVariable UUID id,@PathVariable UUID questionId) {
        return exams.removeQuestion(id,questionId);
    }
    @PostMapping("/{id}/publish") public ExamAdminService.ExamView publish(@PathVariable UUID id) { return exams.publish(id); }
    @PostMapping("/{id}/close") public ExamAdminService.ExamView close(@PathVariable UUID id) { return exams.close(id); }
    @PostMapping("/{id}/assignments") @ResponseStatus(HttpStatus.CREATED)
    public ExamAdminService.AssignmentView assign(@PathVariable UUID id,@RequestBody ExamAdminService.AssignmentInput input,JwtAuthenticationToken principal) {
        return exams.assign(id,input,UUID.fromString(principal.getName()));
    }
}
