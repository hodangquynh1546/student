package com.edutrack.assessment;

import com.edutrack.question.QuestionService;
import com.edutrack.notification.NotificationEventWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExamAdminServiceTest {
    private final JdbcTemplate db=mock(JdbcTemplate.class);
    private final QuestionService questions=mock(QuestionService.class);
    private final ExamAdminService service=spy(new ExamAdminService(db,questions,mock(NotificationEventWriter.class)));

    private ExamAdminService.ExamView exam(String status,long questionCount) {
        return new ExamAdminService.ExamView(UUID.randomUUID(),"Đề toán", "",ExamAdminService.Kind.EXAM,status,null,null,
            45,1,false,false,false,false,true,false,BigDecimal.TEN,questionCount,0);
    }

    @Test void invalidExamTimeRangeIsRejectedBeforeWriting() {
        var input=new ExamAdminService.Input("Đề toán",null,ExamAdminService.Kind.EXAM,
            Instant.parse("2026-11-06T00:00:00Z"),Instant.parse("2026-11-05T00:00:00Z"),45,1,
            false,false,false,false,true,false);
        var error=assertThrows(ResponseStatusException.class,()->service.create(input,UUID.randomUUID()));
        assertEquals(HttpStatus.BAD_REQUEST,error.getStatusCode());
        verifyNoInteractions(db);
    }

    @Test void emptyDraftCannotBePublished() {
        UUID id=UUID.randomUUID();
        doReturn(exam("DRAFT",0)).when(service).get(id);
        var error=assertThrows(ResponseStatusException.class,()->service.publish(id));
        assertEquals(HttpStatus.CONFLICT,error.getStatusCode());
        verifyNoInteractions(db);
    }

    @Test void publishedExamCannotBeModifiedWithQuestion() {
        UUID id=UUID.randomUUID();
        doReturn(exam("PUBLISHED",1)).when(service).get(id);
        var error=assertThrows(ResponseStatusException.class,()->service.addQuestion(id,
            new ExamAdminService.AddQuestion(UUID.randomUUID(),BigDecimal.ONE,null)));
        assertEquals(HttpStatus.CONFLICT,error.getStatusCode());
        verifyNoInteractions(db,questions);
    }

    @Test void assignmentMustTargetExactlyOneClassOrStudent() {
        UUID id=UUID.randomUUID(), actor=UUID.randomUUID();
        doReturn(exam("PUBLISHED",1)).when(service).get(id);
        var none=assertThrows(ResponseStatusException.class,()->service.assign(id,
            new ExamAdminService.AssignmentInput(null,null,null),actor));
        var both=assertThrows(ResponseStatusException.class,()->service.assign(id,
            new ExamAdminService.AssignmentInput(UUID.randomUUID(),UUID.randomUUID(),null),actor));
        assertEquals(HttpStatus.BAD_REQUEST,none.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,both.getStatusCode());
        verifyNoInteractions(db);
    }

    @Test void publishedExamRejectsReorderRemoveAndPointChange() {
        UUID id=UUID.randomUUID(), questionId=UUID.randomUUID();
        doReturn(exam("PUBLISHED",1)).when(service).get(id);
        assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,
            ()->service.reorderQuestions(id,new ExamAdminService.QuestionOrder(List.of(questionId)))).getStatusCode());
        assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,
            ()->service.removeQuestion(id,questionId)).getStatusCode());
        assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,
            ()->service.changePoints(id,questionId,new ExamAdminService.PointsInput(BigDecimal.ONE))).getStatusCode());
        verifyNoInteractions(db);
    }

    @Test void invalidPointChangeIsRejectedBeforeWriting() {
        UUID id=UUID.randomUUID();
        doReturn(exam("DRAFT",1)).when(service).get(id);
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,
            ()->service.changePoints(id,UUID.randomUUID(),new ExamAdminService.PointsInput(BigDecimal.ZERO))).getStatusCode());
        verifyNoInteractions(db);
    }

    @Test void reorderRequiresExactQuestionPermutation() {
        UUID id=UUID.randomUUID(), first=UUID.randomUUID(), second=UUID.randomUUID();
        doReturn(exam("DRAFT",2)).when(service).get(id);
        when(db.queryForObject(anyString(),eq(String.class),eq(id))).thenReturn("DRAFT");
        when(db.query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),eq(id))).thenReturn(List.of(first,second));
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,
            ()->service.reorderQuestions(id,new ExamAdminService.QuestionOrder(List.of(first,first)))).getStatusCode());
        verify(db,never()).update(anyString(),any(Object[].class));
    }
}
