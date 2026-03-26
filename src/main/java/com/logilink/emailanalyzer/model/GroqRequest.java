package com.logilink.emailanalyzer.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroqRequest(
        String model,
        List<Message> messages,
        Double temperature,
        Integer max_tokens
) {
    public GroqRequest(String model, List<Message> messages) {
        this(model, messages, null, null);
    }

    public GroqRequest(String model, List<Message> messages, Double temperature) {
        this(model, messages, temperature, null);
    }

    public record Message(String role, String content) {
    }
}
