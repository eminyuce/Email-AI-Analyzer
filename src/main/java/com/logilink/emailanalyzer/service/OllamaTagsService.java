package com.logilink.emailanalyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class OllamaTagsService {

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper;

    public OllamaTagsService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> listModelNames(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("Ollama base URL is empty");
        }
        String normalized = baseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        String tagsUrl = normalized + "/api/tags";
        String body;
        try {
            body = restClient.get()
                    .uri(tagsUrl)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Could not reach Ollama at " + tagsUrl + ": " + e.getMessage(), e);
        }
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode models = root.get("models");
            if (models == null || !models.isArray()) {
                return List.of();
            }
            List<String> names = new ArrayList<>();
            for (JsonNode m : models) {
                if (m.hasNonNull("name")) {
                    names.add(m.get("name").asText());
                }
            }
            Collections.sort(names);
            return names;
        } catch (Exception e) {
            throw new IllegalStateException("Invalid response from Ollama /api/tags: " + e.getMessage(), e);
        }
    }
}
