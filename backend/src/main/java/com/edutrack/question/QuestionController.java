package com.edutrack.question;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/questions")
@PreAuthorize("hasRole('ADMIN_TEACHER')")
public class QuestionController {
    private final QuestionService questions;
    public QuestionController(QuestionService questions) { this.questions=questions; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public QuestionService.View create(@RequestBody QuestionService.Input input,JwtAuthenticationToken principal) {
        return questions.create(input,UUID.fromString(principal.getName()));
    }
    @GetMapping("/{id}")
    public QuestionService.View get(@PathVariable UUID id) { return questions.get(id); }
    @GetMapping
    public QuestionService.Page list(@RequestParam(required=false) String subject,@RequestParam(required=false) String topic,
                                     @RequestParam(required=false) QuestionService.Type type,
                                     @RequestParam(required=false) QuestionService.Difficulty difficulty,
                                     @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return questions.list(subject,topic,type,difficulty,page,size);
    }
    @PutMapping("/{id}")
    public QuestionService.View update(@PathVariable UUID id,@RequestParam long version,@RequestBody QuestionService.Input input) {
        return questions.update(id,input,version);
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id,@RequestParam long version) { questions.delete(id,version); }
}
