package com.logilink.emailanalyzer.service;

import com.logilink.emailanalyzer.domain.AppSettings;
import com.logilink.emailanalyzer.exception.EmailAnalysisException;
import com.logilink.emailanalyzer.model.EmailAnalysisResult;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);

    private final AppSettingsService appSettingsService;

    public AIService(AppSettingsService appSettingsService) {
        this.appSettingsService = appSettingsService;
    }

    public EmailAnalysisResult analyzeEmail(String emailId, String subject, String sender, String content) {
        return analyzeEmail(emailId, subject, sender, content, null);
    }

    public EmailAnalysisResult analyzeEmail(String emailId, String subject, String sender, String content, String systemPromptOverride) {
        try {
            AppSettings settings = appSettingsService.getOrCreate();
            ChatClient chatClient = createChatClient(settings);
            String systemPrompt = StringUtils.isNotBlank(systemPromptOverride)
                    ? systemPromptOverride
                    : settings.getSystemPrompt();
            log.debug("AI system prompt: {}", systemPrompt);
            String userPrompt = String.format("""
                    Email ID: %s
                    Subject: %s
                    Sender: %s
                    Content:
                    %s
                    """, emailId, subject, sender, content);
            log.debug("AI user prompt: {}", userPrompt);
            var result = chatClient.prompt()
                    .system(spec -> spec.text("{systemPrompt}").params(Map.of("systemPrompt", systemPrompt)))
                    .user(spec -> spec.text("{userPrompt}").params(Map.of("userPrompt", userPrompt)))
                    .call()
                    .entity(EmailAnalysisResult.class);
            log.debug("AI analysis result: {}", result);
            return result;
        } catch (Exception e) {
            log.error(
                    "Error during AI analysis for email {}. subject='{}', sender='{}', contentLength={}, rootError={}",
                    emailId,
                    subject,
                    sender,
                    content == null ? 0 : content.length(),
                    e.getMessage(),
                    e
            );
            throw new EmailAnalysisException("AI Analysis failed", e);
        }
    }

    private ChatClient createChatClient(AppSettings settings) {
        String baseUrl = normalizeBaseUrl(settings.getLlmUrl());
        if (StringUtils.isBlank(baseUrl)) {
            throw new EmailAnalysisException("LLM URL is not configured in application settings.");
        }
        if (StringUtils.isBlank(settings.getLlmModel())) {
            throw new EmailAnalysisException("LLM model is not configured in application settings.");
        }
        float temperature = settings.getLlmTemperature() == null
                ? 0.3f
                : settings.getLlmTemperature().floatValue();

        OllamaApi ollamaApi = new OllamaApi(baseUrl);
        OllamaOptions options = OllamaOptions.create()
                .withModel(settings.getLlmModel())
                .withTemperature(temperature);

        OllamaChatModel chatModel = new OllamaChatModel(ollamaApi, options);
        return ChatClient.builder(chatModel).build();
    }

    private static String normalizeBaseUrl(String llmUrl) {
        if (StringUtils.isBlank(llmUrl)) {
            return "";
        }
        String u = llmUrl.trim();
        if (u.endsWith("/")) {
            return u.substring(0, u.length() - 1);
        }
        return u;
    }
}
