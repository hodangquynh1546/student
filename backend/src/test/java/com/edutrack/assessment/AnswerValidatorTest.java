package com.edutrack.assessment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AnswerValidatorTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void rejectsChoiceOutsideFrozenQuestion() throws Exception {
        var content = json.readTree("{\"options\":[{\"id\":\"a\"},{\"id\":\"b\"}]}");
        assertThatCode(() -> AnswerValidator.validate("SINGLE_CHOICE", content, json.readTree("{\"selectedId\":\"a\"}")))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> AnswerValidator.validate("SINGLE_CHOICE", content, json.readTree("{\"selectedId\":\"c\"}")))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsDuplicatedOrUnknownOrderingItems() throws Exception {
        var content = json.readTree("{\"items\":[{\"id\":\"1\"},{\"id\":\"2\"}]}");
        assertThatCode(() -> AnswerValidator.validate("ORDERING", content, json.readTree("{\"order\":[\"2\",\"1\"]}")))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> AnswerValidator.validate("ORDERING", content, json.readTree("{\"order\":[\"1\",\"1\"]}")))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void allowsEmptyAnswerForReviewFlagButRejectsUnknownFields() throws Exception {
        var content = json.readTree("{}");
        assertThatCode(() -> AnswerValidator.validate("ESSAY", content, json.readTree("{}"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> AnswerValidator.validate("ESSAY", content, json.readTree("{\"text\":\"hello\",\"answerKey\":\"x\"}")))
            .isInstanceOf(ResponseStatusException.class);
    }
}
