package com.jobtracker.careerflow.database;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/** Finds complete question/answer objects inside the model's streamed JSON output. */
final class IncrementalInterviewJsonParser {
    private final ObjectMapper mapper;
    private final Consumer<JsonNode> onQuestion;
    private final StringBuilder source = new StringBuilder();
    private final Deque<Integer> objectStarts = new ArrayDeque<>();
    private int scanned;
    private boolean inString;
    private boolean escaped;

    IncrementalInterviewJsonParser(ObjectMapper mapper, Consumer<JsonNode> onQuestion) {
        this.mapper = mapper;
        this.onQuestion = onQuestion;
    }

    void accept(String chunk) {
        source.append(chunk);
        for (; scanned < source.length(); scanned++) {
            char current = source.charAt(scanned);
            if (inString) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') inString = false;
                continue;
            }
            if (current == '"') inString = true;
            else if (current == '{') objectStarts.push(scanned);
            else if (current == '}' && !objectStarts.isEmpty()) {
                int start = objectStarts.pop();
                JsonNode object;
                try {
                    object = mapper.readTree(source.substring(start, scanned + 1));
                } catch (Exception ignored) {
                    // Most closed objects are topic/root objects; only valid question/answer objects are emitted.
                    continue;
                }
                if (object != null && object.path("question").isTextual()
                    && object.path("answer").isTextual()
                    && !object.path("question").asText().isBlank()
                    && !object.path("answer").asText().isBlank()) onQuestion.accept(object);
            }
        }
    }
}
