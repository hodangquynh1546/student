package com.edutrack.assessment;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Deterministic grading. Policy is frozen inside each exam question's answer key. */
public final class ObjectiveGrader {
    private ObjectiveGrader() {}

    public static BigDecimal score(String type,JsonNode key,JsonNode answer,BigDecimal points) {
        if(key==null || answer==null || !answer.isObject() || points==null) return BigDecimal.ZERO;
        boolean partial="PARTIAL".equals(key.path("gradingPolicy").path("mode").asText());
        int correct=0,total=1;
        switch(type) {
            case "SINGLE_CHOICE" -> correct=equalText(key.path("correctId"),answer.path("selectedId"))?1:0;
            case "TRUE_FALSE" -> correct=answer.path("value").isBoolean() && key.path("correct").asBoolean()==answer.path("value").asBoolean()?1:0;
            case "SHORT_ANSWER" -> correct=shortAnswer(key,answer)?1:0;
            case "MULTIPLE_CHOICE" -> {
                JsonNode expected=key.path("correctIds"),selected=answer.path("selectedIds");
                if(!expected.isArray() || !selected.isArray() || expected.isEmpty()) return BigDecimal.ZERO;
                Set<String> target=textSet(expected),actual=textSet(selected);
                if(target==null || actual==null) return BigDecimal.ZERO;
                total=target.size();
                if(partial) {int matched=0;for(String id:actual) if(target.contains(id)) matched++;correct=Math.max(0,matched-(actual.size()-matched));}
                else correct=target.equals(actual)?total:0;
            }
            case "MATCHING" -> {
                JsonNode expected=key.path("pairs"),actual=answer.path("pairs");
                if(!expected.isObject() || !actual.isObject() || expected.isEmpty()) return BigDecimal.ZERO;
                total=expected.size();
                if(!partial && actual.size()!=total) return BigDecimal.ZERO;
                var fields=expected.fields();while(fields.hasNext()) {var entry=fields.next();if(equalText(entry.getValue(),actual.path(entry.getKey()))) correct++;}
            }
            case "ORDERING" -> {
                JsonNode expected=key.path("order"),actual=answer.path("order");
                if(!expected.isArray() || !actual.isArray() || expected.isEmpty() || expected.size()!=actual.size()) return BigDecimal.ZERO;
                Set<String> target=textSet(expected),selected=textSet(actual);
                if(target==null || selected==null || !target.equals(selected)) return BigDecimal.ZERO;
                total=expected.size();for(int i=0;i<total;i++) if(equalText(expected.get(i),actual.get(i))) correct++;
            }
            case "FILL_IN_THE_BLANK" -> {
                JsonNode expected=key.path("answers"),actual=answer.path("blanks");
                if(!expected.isObject() || !actual.isObject() || expected.isEmpty()) return BigDecimal.ZERO;
                total=expected.size();
                if(!partial && actual.size()!=total) return BigDecimal.ZERO;
                var fields=expected.fields();while(fields.hasNext()) {
                    var entry=fields.next();JsonNode value=actual.path(entry.getKey());
                    if(!value.isTextual() || value.asText().isBlank()) continue;
                    for(JsonNode accepted:entry.getValue()) if(equalAnswer(key,value.asText(),accepted.asText())) {correct++;break;}
                }
            }
            default -> {return BigDecimal.ZERO;}
        }
        if(!partial && correct!=total) return BigDecimal.ZERO;
        return points.multiply(BigDecimal.valueOf(correct)).divide(BigDecimal.valueOf(total),2,RoundingMode.HALF_UP);
    }

    private static boolean shortAnswer(JsonNode key,JsonNode answer) {
        if(!answer.path("text").isTextual() || answer.path("text").asText().isBlank()) return false;
        for(JsonNode accepted:key.path("acceptedAnswers"))
            if(accepted.isTextual() && equalAnswer(key,answer.path("text").asText(),accepted.asText())) return true;
        return false;
    }
    private static boolean equalAnswer(JsonNode key,String answer,String expected) {
        JsonNode policy=key.path("gradingPolicy");
        boolean normalize=policy.path("normalizeWhitespace").asBoolean(true);
        boolean sensitive=policy.has("caseSensitive")?policy.path("caseSensitive").asBoolean():key.path("caseSensitive").asBoolean(false);
        String left=normalize?answer.strip().replaceAll("(?U)\\s+"," "):answer;
        String right=normalize?expected.strip().replaceAll("(?U)\\s+"," "):expected;
        return sensitive?left.equals(right):left.toLowerCase(Locale.ROOT).equals(right.toLowerCase(Locale.ROOT));
    }
    private static boolean equalText(JsonNode a,JsonNode b) {return a.isTextual() && b.isTextual() && a.asText().equals(b.asText());}
    private static Set<String> textSet(JsonNode array) {
        Set<String> values=new HashSet<>();
        for(JsonNode item:array) if(!item.isTextual() || !values.add(item.asText())) return null;
        return values;
    }
}
