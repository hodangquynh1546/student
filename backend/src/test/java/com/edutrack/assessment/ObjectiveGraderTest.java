package com.edutrack.assessment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ObjectiveGraderTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final BigDecimal points=new BigDecimal("2.50");
    private BigDecimal score(String type,String key,String answer) throws Exception {
        JsonNode a=answer==null?null:mapper.readTree(answer);
        return ObjectiveGrader.score(type,mapper.readTree(key),a,points);
    }
    @Test void gradesAllObjectiveTypes() throws Exception {
        assertEquals(points,score("SINGLE_CHOICE","{\"correctId\":\"b\"}","{\"selectedId\":\"b\"}"));
        assertEquals(points,score("MULTIPLE_CHOICE","{\"correctIds\":[\"a\",\"c\"]}","{\"selectedIds\":[\"c\",\"a\"]}"));
        assertEquals(points,score("TRUE_FALSE","{\"correct\":false}","{\"value\":false}"));
        assertEquals(points,score("SHORT_ANSWER","{\"acceptedAnswers\":[\"Tokyo\"],\"caseSensitive\":false}","{\"text\":\" tokyo \"}"));
        assertEquals(points,score("MATCHING","{\"pairs\":{\"a\":\"2\",\"b\":\"1\"}}","{\"pairs\":{\"b\":\"1\",\"a\":\"2\"}}"));
        assertEquals(points,score("ORDERING","{\"order\":[\"a\",\"b\"]}","{\"order\":[\"a\",\"b\"]}"));
        assertEquals(points,score("FILL_IN_THE_BLANK","{\"answers\":{\"x\":[\"Paris\"]}}","{\"blanks\":{\"x\":\" paris \"}}"));
    }
    @Test void rejectsIncompleteAndForgedAnswers() throws Exception {
        assertEquals(BigDecimal.ZERO,score("MULTIPLE_CHOICE","{\"correctIds\":[\"a\",\"b\"]}","{\"selectedIds\":[\"a\",\"a\"]}"));
        assertEquals(BigDecimal.ZERO,score("MATCHING","{\"pairs\":{\"a\":\"1\",\"b\":\"2\"}}","{\"pairs\":{\"a\":\"1\"}}"));
        assertEquals(BigDecimal.ZERO,score("ORDERING","{\"order\":[\"a\",\"b\"]}","{\"order\":[\"b\",\"a\"]}"));
        assertEquals(BigDecimal.ZERO,score("ESSAY","{\"referenceAnswer\":\"secret\"}","{\"text\":\"secret\"}"));
        assertEquals(BigDecimal.ZERO,score("SINGLE_CHOICE","{\"correctId\":\"a\"}",null));
    }
    @Test void partialCreditPenalizesWrongSelectionsAndRounds() throws Exception {
        assertEquals(new BigDecimal("1.25"),score("MULTIPLE_CHOICE","{\"correctIds\":[\"a\",\"b\"],\"gradingPolicy\":{\"mode\":\"PARTIAL\"}}","{\"selectedIds\":[\"a\"]}"));
        assertEquals(BigDecimal.ZERO.setScale(2),score("MULTIPLE_CHOICE","{\"correctIds\":[\"a\",\"b\"],\"gradingPolicy\":{\"mode\":\"PARTIAL\"}}","{\"selectedIds\":[\"a\",\"x\"]}"));
        assertEquals(new BigDecimal("1.25"),score("MATCHING","{\"pairs\":{\"a\":\"1\",\"b\":\"2\"},\"gradingPolicy\":{\"mode\":\"PARTIAL\"}}","{\"pairs\":{\"a\":\"1\"}}"));
        assertEquals(new BigDecimal("1.25"),score("FILL_IN_THE_BLANK","{\"answers\":{\"a\":[\"red\"],\"b\":[\"blue\"]},\"gradingPolicy\":{\"mode\":\"PARTIAL\"}}","{\"blanks\":{\"a\":\" red \"}}"));
    }
    @Test void normalizationAndCaseSensitivityFollowPolicy() throws Exception {
        assertEquals(points,score("SHORT_ANSWER","{\"acceptedAnswers\":[\"New York\"]}","{\"text\":\" new   york \"}"));
        assertEquals(BigDecimal.ZERO,score("SHORT_ANSWER","{\"acceptedAnswers\":[\"New York\"],\"gradingPolicy\":{\"caseSensitive\":true}}","{\"text\":\"new york\"}"));
        assertEquals(BigDecimal.ZERO,score("SHORT_ANSWER","{\"acceptedAnswers\":[\"New York\"],\"gradingPolicy\":{\"normalizeWhitespace\":false}}","{\"text\":\"New  York\"}"));
    }
}
