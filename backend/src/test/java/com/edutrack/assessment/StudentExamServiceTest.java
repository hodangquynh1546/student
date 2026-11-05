package com.edutrack.assessment;

import com.edutrack.notification.NotificationEventWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class StudentExamServiceTest {
    private final ObjectMapper json=new ObjectMapper();
    private final StudentExamService exams=new StudentExamService(mock(JdbcTemplate.class),json,mock(NotificationEventWriter.class));

    @Test
    void studentQuestionContentDropsNestedAnswerMaterial() throws Exception {
        var source=json.readTree("""
            {"options":[{"id":"a","text":"A","correct":true,"answerKey":{"secret":1}},
                        {"id":"b","text":"B","meta":{"correctId":"b"}}],
             "correctId":"a","rubric":"private","explanation":"private"}
            """);
        var safe=exams.publicContent("SINGLE_CHOICE",source);
        assertEquals("a",safe.path("options").get(0).path("id").asText());
        assertEquals("A",safe.path("options").get(0).path("text").asText());
        assertFalse(safe.toString().contains("correct"));
        assertFalse(safe.toString().contains("answerKey"));
        assertFalse(safe.toString().contains("rubric"));
        assertFalse(safe.toString().contains("private"));
    }

    @Test
    void unknownQuestionContentHasNoStudentVisibleSecrets() throws Exception {
        var source=json.readTree("{\"answer\":\"secret\",\"other\":{\"isCorrect\":true}}");
        assertEquals("{}",exams.publicContent("FUTURE_TYPE",source).toString());
    }
}
