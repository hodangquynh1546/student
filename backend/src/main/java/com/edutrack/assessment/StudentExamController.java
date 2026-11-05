package com.edutrack.assessment;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentExamController {
    private final StudentExamService exams;
    public StudentExamController(StudentExamService exams) {this.exams=exams;}
    @GetMapping("/exams")
    public StudentExamService.Page list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
                                         JwtAuthenticationToken principal) {
        return exams.list(UUID.fromString(principal.getName()),page,size);
    }
    @PostMapping("/exams/{id}/attempts")
    public ResponseEntity<StudentExamService.AttemptView> start(@PathVariable UUID id,JwtAuthenticationToken principal) {
        var result=exams.start(id,UUID.fromString(principal.getName()));
        return ResponseEntity.status(result.created()?HttpStatus.CREATED:HttpStatus.OK).body(result.attempt());
    }
    @GetMapping("/attempts/{id}")
    public StudentExamService.AttemptView get(@PathVariable UUID id,JwtAuthenticationToken principal) {
        return exams.get(id,UUID.fromString(principal.getName()));
    }
    @PutMapping("/attempts/{id}/answers/{questionId}")
    public StudentExamService.SavedAnswer save(@PathVariable UUID id,@PathVariable UUID questionId,
                                                @RequestBody StudentExamService.AnswerInput input,JwtAuthenticationToken principal) {
        return exams.save(id,questionId,UUID.fromString(principal.getName()),input);
    }
    @PostMapping("/attempts/{id}/submit")
    public StudentExamService.Submission submit(@PathVariable UUID id,@RequestBody StudentExamService.SubmitInput input,
                                                 JwtAuthenticationToken principal) {
        return exams.submit(id,UUID.fromString(principal.getName()),input);
    }
    @GetMapping("/attempts/{id}/result")
    public StudentExamService.Result result(@PathVariable UUID id,JwtAuthenticationToken principal) {
        return exams.result(id,UUID.fromString(principal.getName()));
    }
}
