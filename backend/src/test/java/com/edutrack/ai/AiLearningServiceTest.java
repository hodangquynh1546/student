package com.edutrack.ai;

import com.edutrack.assessment.GeminiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiLearningServiceTest {
    private final JdbcTemplate db=mock(JdbcTemplate.class);
    private final GeminiClient gemini=mock(GeminiClient.class);
    private final AiLearningService service=new AiLearningService(db,gemini,new ObjectMapper());

    @Test void rejectsUnpublishedOrForeignAttemptBeforeAiCall() {
        when(db.query(anyString(),any(RowMapper.class),any(),any(),any(),any())).thenReturn(List.of());
        when(gemini.configured()).thenReturn(true);
        var error=assertThrows(ResponseStatusException.class,()->service.ask(UUID.randomUUID(),
            new AiLearningService.AskInput("Giải thích kiến thức này",UUID.randomUUID(),UUID.randomUUID())));
        assertEquals(HttpStatus.NOT_FOUND,error.getStatusCode());
        verify(gemini,never()).generateText(anyString());
    }

    @Test void unavailableWhenAiNotConfigured() {
        var error=assertThrows(ResponseStatusException.class,()->service.ask(UUID.randomUUID(),
            new AiLearningService.AskInput("Giải thích phân số",null,null)));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,error.getStatusCode());
    }

    @Test void teacherDraftRequiresValidJsonAndIsNotSaved() {
        when(gemini.configured()).thenReturn(true);
        when(gemini.generateText(anyString())).thenReturn("[{\"type\":\"SINGLE_CHOICE\",\"body\":\"2+2=?\",\"content\":{},\"answerKey\":{\"correctId\":\"a\"}}]");
        var input=new AiLearningService.GenerateInput("Toán","Cộng","4","EASY","SINGLE_CHOICE",1);
        var response=service.generate(UUID.randomUUID(),input);
        assertEquals(1,response.drafts().size());
        verifyNoInteractions(db);
    }

    @Test void invalidDraftIsRejected() {
        when(gemini.configured()).thenReturn(true);
        when(gemini.generateText(anyString())).thenReturn("[]");
        var error=assertThrows(ResponseStatusException.class,()->service.generate(UUID.randomUUID(),
            new AiLearningService.GenerateInput("Toán","Cộng","4","EASY","SINGLE_CHOICE",1)));
        assertEquals(HttpStatus.BAD_GATEWAY,error.getStatusCode());
    }

    @Test void aiCallLimitIsEnforcedPerStudent() {
        when(gemini.configured()).thenReturn(true);
        when(gemini.generateText(anyString())).thenReturn("Hãy ôn lại định nghĩa phân số.");
        UUID student=UUID.randomUUID();
        var input=new AiLearningService.AskInput("Phân số là gì?",null,null);
        for(int i=0;i<12;i++) assertNotNull(service.ask(student,input).answer());
        var error=assertThrows(ResponseStatusException.class,()->service.ask(student,input));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,error.getStatusCode());
        verify(gemini,times(12)).generateText(anyString());
    }

    @Test void rejectsMissingContextPairAndOversizedQuestions() {
        UUID student=UUID.randomUUID();
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,()->service.ask(student,
            new AiLearningService.AskInput("Giải thích",UUID.randomUUID(),null))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,()->service.ask(student,
            new AiLearningService.AskInput("x".repeat(1501),null,null))).getStatusCode());
        verifyNoInteractions(db,gemini);
    }

    @Test void activeAttemptBlocksGenericQuestionsBeforeAiCall() {
        UUID student=UUID.randomUUID();
        when(db.queryForObject(anyString(),eq(Boolean.class),eq(student))).thenReturn(true);
        var error=assertThrows(ResponseStatusException.class,()->service.ask(student,
            new AiLearningService.AskInput("Đáp án câu đang thi là gì?",null,null)));
        assertEquals(HttpStatus.CONFLICT,error.getStatusCode());
        verifyNoInteractions(gemini);
    }
}
