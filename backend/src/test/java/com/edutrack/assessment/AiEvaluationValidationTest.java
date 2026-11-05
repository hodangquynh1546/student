package com.edutrack.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiEvaluationValidationTest {
    private final ObjectMapper json=new ObjectMapper();

    @Test
    void acceptsCompleteBoundedEvaluation() throws Exception {
        var result=AdminGradingService.validated(UUID.randomUUID(),new BigDecimal("10.00"),json.readTree("""
            {"suggestedScore":7.5,"maxScore":10,"evaluation":"Có lập luận",
             "strengths":["Nêu đúng ý chính"],"weaknesses":[],"mistakes":[],
             "improvementSuggestions":["Bổ sung ví dụ"],"confidence":0.8,
             "gradingReason":"Theo rubric"}
            """),"model-from-env",12,Instant.now());
        assertEquals(new BigDecimal("7.5"),result.suggestedScore());
        assertEquals("model-from-env",result.model());
        assertEquals(1,result.strengths().size());
    }

    @Test
    void rejectsScoreBeyondMaximumAndMismatchedMaximum() throws Exception {
        String source="""
            {"suggestedScore":11,"maxScore":10,"evaluation":"Có lập luận",
             "strengths":[],"weaknesses":[],"mistakes":[],"improvementSuggestions":[],
             "confidence":0.8,"gradingReason":"Theo rubric"}
            """;
        assertThrows(IllegalArgumentException.class,()->AdminGradingService.validated(UUID.randomUUID(),
            BigDecimal.TEN,json.readTree(source),"model",1,Instant.now()));
        assertThrows(IllegalArgumentException.class,()->AdminGradingService.validated(UUID.randomUUID(),
            BigDecimal.valueOf(12),json.readTree(source.replace("11","9")),"model",1,Instant.now()));
    }

    @Test
    void rejectsMissingExplanationsAndInvalidConfidence() throws Exception {
        var missing=json.readTree("""
            {"suggestedScore":5,"maxScore":10,"evaluation":"OK","strengths":[],
             "weaknesses":[],"mistakes":[],"confidence":0.5,"gradingReason":"Theo rubric"}
            """);
        assertThrows(IllegalArgumentException.class,()->AdminGradingService.validated(UUID.randomUUID(),
            BigDecimal.TEN,missing,"model",1,Instant.now()));
        var confidence=json.readTree("""
            {"suggestedScore":5,"maxScore":10,"evaluation":"OK","strengths":[],
             "weaknesses":[],"mistakes":[],"improvementSuggestions":[],"confidence":1.2,
             "gradingReason":"Theo rubric"}
            """);
        assertThrows(IllegalArgumentException.class,()->AdminGradingService.validated(UUID.randomUUID(),
            BigDecimal.TEN,confidence,"model",1,Instant.now()));
    }

    @Test
    void missingGeminiConfigurationIsExplicit() {
        var client=new GeminiClient(json,"","",10);
        var error=assertThrows(ResponseStatusException.class,()->client.generateStructured("prompt",List.of()));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,error.getStatusCode());
    }

    @Test
    void rejectsUnsupportedMediaBeforeAnyProviderCall() {
        var client=new GeminiClient(json,"key","model",10);
        var error=assertThrows(ResponseStatusException.class,()->client.generateStructured("prompt",
            List.of(new GeminiClient.Media(new byte[]{1,2,3},"application/octet-stream"))));
        assertEquals(HttpStatus.BAD_REQUEST,error.getStatusCode());
    }
}
