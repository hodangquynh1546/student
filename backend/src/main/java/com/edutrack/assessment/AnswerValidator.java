package com.edutrack.assessment;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Validates student-owned payloads against the frozen public question content. */
final class AnswerValidator {
    private AnswerValidator() { }

    static void validate(String type, JsonNode content, JsonNode answer) {
        if (answer == null || !answer.isObject() || answer.toString().length() > 50_000)
            throw invalid();
        if (answer.isEmpty()) return; // Review flag may be saved before answering.
        switch (type) {
            case "SINGLE_CHOICE" -> {
                only(answer, "selectedId");
                if (!member(content.path("options"), answer.path("selectedId"))) throw invalid();
            }
            case "MULTIPLE_CHOICE" -> {
                only(answer, "selectedIds");
                JsonNode selected = answer.path("selectedIds");
                if (!selected.isArray() || selected.size() > 20) throw invalid();
                Set<String> unique = new HashSet<>();
                for (JsonNode id : selected) if (!member(content.path("options"), id) || !unique.add(id.asText())) throw invalid();
            }
            case "TRUE_FALSE" -> {
                only(answer, "value");
                if (!answer.path("value").isBoolean()) throw invalid();
            }
            case "SHORT_ANSWER", "ESSAY" -> {
                only(answer, "text");
                JsonNode text = answer.path("text");
                if (!text.isTextual() || text.asText().length() > ("ESSAY".equals(type) ? 30_000 : 2_000)) throw invalid();
            }
            case "MATCHING" -> {
                only(answer, "pairs");
                JsonNode pairs = answer.path("pairs");
                if (!pairs.isObject() || pairs.size() > content.path("left").size()) throw invalid();
                var entries = pairs.fields();
                while (entries.hasNext()) {
                    var pair = entries.next();
                    if (!containsId(content.path("left"), pair.getKey()) ||
                        !(pair.getValue().isTextual() && (pair.getValue().asText().isEmpty() ||
                          containsId(content.path("right"), pair.getValue().asText())))) throw invalid();
                }
            }
            case "ORDERING" -> {
                only(answer, "order");
                JsonNode order = answer.path("order");
                if (!order.isArray() || order.size() != content.path("items").size()) throw invalid();
                Set<String> unique = new HashSet<>();
                for (JsonNode id : order) if (!member(content.path("items"), id) || !unique.add(id.asText())) throw invalid();
            }
            case "FILL_IN_THE_BLANK" -> {
                only(answer, "blanks");
                JsonNode blanks = answer.path("blanks");
                if (!blanks.isObject() || blanks.size() > content.path("blanks").size()) throw invalid();
                var entries = blanks.fields();
                while (entries.hasNext()) {
                    var entry = entries.next();
                    if (!containsId(content.path("blanks"), entry.getKey()) || !entry.getValue().isTextual() ||
                        entry.getValue().asText().length() > 2_000) throw invalid();
                }
            }
            default -> throw invalid();
        }
    }

    private static void only(JsonNode answer, String expected) {
        if (answer.size() != 1 || !answer.has(expected)) throw invalid();
    }
    private static boolean member(JsonNode items, JsonNode id) {
        return id.isTextual() && containsId(items, id.asText());
    }
    private static boolean containsId(JsonNode items, String id) {
        if (!items.isArray()) return false;
        for (JsonNode item : items) if (id.equals(item.path("id").asText())) return true;
        return false;
    }
    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Câu trả lời không hợp lệ");
    }
}
