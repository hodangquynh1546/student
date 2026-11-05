package com.edutrack.question;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class QuestionServiceTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final QuestionService service=new QuestionService(null,JSON);
    private static JsonNode node(String value) {
        try { return JSON.readTree(value); } catch(Exception e) { throw new RuntimeException(e); }
    }
    private static QuestionService.Input input(QuestionService.Type type,String content,String answer,String rubric) {
        return new QuestionService.Input(type,"Toán",null,"Phân số",null,null,QuestionService.Difficulty.EASY,
            "Nội dung câu hỏi",node(content),node(answer),null,rubric,BigDecimal.ONE);
    }
    static Stream<Arguments> supportedTypes() {
        String options="{\"options\":[{\"id\":\"A\",\"text\":\"Một\"},{\"id\":\"B\",\"text\":\"Hai\"}]}";
        return Stream.of(
            Arguments.of(input(QuestionService.Type.SINGLE_CHOICE,options,"{\"correctId\":\"A\"}",null)),
            Arguments.of(input(QuestionService.Type.MULTIPLE_CHOICE,options,"{\"correctIds\":[\"A\",\"B\"]}",null)),
            Arguments.of(input(QuestionService.Type.TRUE_FALSE,"{}","{\"correct\":true}",null)),
            Arguments.of(input(QuestionService.Type.SHORT_ANSWER,"{}","{\"acceptedAnswers\":[\"1/2\"]}",null)),
            Arguments.of(input(QuestionService.Type.ESSAY,"{}","{\"referenceAnswer\":\"Giải thích\"}","Thang điểm")),
            Arguments.of(input(QuestionService.Type.MATCHING,"{\"left\":[{\"id\":\"L1\",\"text\":\"Một\"},{\"id\":\"L2\",\"text\":\"Hai\"}],\"right\":[{\"id\":\"R1\",\"text\":\"1\"},{\"id\":\"R2\",\"text\":\"2\"}]}","{\"pairs\":{\"L1\":\"R1\",\"L2\":\"R2\"}}",null)),
            Arguments.of(input(QuestionService.Type.ORDERING,"{\"items\":[{\"id\":\"1\",\"text\":\"Một\"},{\"id\":\"2\",\"text\":\"Hai\"}]}","{\"order\":[\"1\",\"2\"]}",null)),
            Arguments.of(input(QuestionService.Type.FILL_IN_THE_BLANK,"{\"blanks\":[{\"id\":\"b1\",\"text\":\"Ô trống\"}]}","{\"answers\":{\"b1\":[\"đúng\"]}}",null))
        );
    }
    @ParameterizedTest @MethodSource("supportedTypes")
    void acceptsAllSupportedTypes(QuestionService.Input input) { assertDoesNotThrow(() -> service.validate(input)); }
    @Test void rejectsAnswerOutsideOptions() {
        var input=input(QuestionService.Type.SINGLE_CHOICE,"{\"options\":[{\"id\":\"A\",\"text\":\"Một\"},{\"id\":\"B\",\"text\":\"Hai\"}]}","{\"correctId\":\"X\"}",null);
        assertThrows(ResponseStatusException.class,() -> service.validate(input));
    }
    @Test void requiresEssayRubric() {
        assertThrows(ResponseStatusException.class,() -> service.validate(input(QuestionService.Type.ESSAY,"{}","{\"referenceAnswer\":\"X\"}",null)));
    }
    @Test void rejectsDuplicateOrderingAnswer() {
        var input=input(QuestionService.Type.ORDERING,"{\"items\":[{\"id\":\"1\",\"text\":\"Một\"},{\"id\":\"2\",\"text\":\"Hai\"}]}","{\"order\":[\"1\",\"1\"]}",null);
        assertThrows(ResponseStatusException.class,() -> service.validate(input));
    }
}
