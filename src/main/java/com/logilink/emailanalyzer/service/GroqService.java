package com.logilink.emailanalyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logilink.emailanalyzer.client.GroqHttpClient;
import com.logilink.emailanalyzer.model.GroqRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GroqService {

    private final GroqHttpClient client;
    private final ObjectMapper mapper;

    public String chat(String model, List<GroqRequest.Message> messages, Double temperature) {
        GroqRequest request = new GroqRequest(model, messages, temperature);
        return extractAssistantContent(client.chat(request));
    }

    public String chat(String model, List<GroqRequest.Message> messages, Double temperature, Integer maxTokens) {
        GroqRequest request = new GroqRequest(model, messages, temperature, maxTokens);
        return extractAssistantContent(client.chat(request));
    }

    private String extractAssistantContent(String response) {
        try {
            JsonNode json = mapper.readTree(response);
            JsonNode choices = json.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new IllegalStateException("Groq response missing choices");
            }
            return choices.get(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw new RuntimeException("Groq failed", e);
        }
    }
}
