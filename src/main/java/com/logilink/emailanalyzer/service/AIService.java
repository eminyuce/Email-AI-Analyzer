package com.logilink.emailanalyzer.service;

import com.logilink.emailanalyzer.domain.AppSettings;
import com.logilink.emailanalyzer.exception.EmailAnalysisException;
import com.logilink.emailanalyzer.model.EmailAnalysisResult;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);

    private final ChatClient.Builder chatClientBuilder;
    private final AppSettingsService appSettingsService;

    public AIService(ChatClient.Builder builder, AppSettingsService appSettingsService) {
        this.chatClientBuilder = builder;
        this.appSettingsService = appSettingsService;
    }

    public EmailAnalysisResult analyzeEmail(String emailId, String subject, String sender, String content) {
        return analyzeEmail(emailId, subject, sender, content, null);
    }

    public EmailAnalysisResult analyzeEmail(String emailId, String subject, String sender, String content, String systemPromptOverride) {
        try {
            AppSettings settings = appSettingsService.getOrCreate();
            ChatClient chatClient = buildChatClient(settings);
            String systemPrompt = StringUtils.isNotBlank(systemPromptOverride)
                    ? systemPromptOverride
                    : settings.getSystemPrompt();

            String userPrompt = String.format("""
                    Email ID: %s
                    Subject: %s
                    Sender: %s
                    Content:
                    %s
                    """, emailId, subject, sender, content);

            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(EmailAnalysisResult.class);
        } catch (Exception e) {
            log.error("Error during AI analysis for email {}: {}", emailId, e.getMessage());
            throw new EmailAnalysisException("AI Analysis failed", e);
        }
    }

    public String testModelResponse(String prompt) {
        String effectivePrompt = (prompt == null || prompt.isBlank())
                ? "Reply with exactly: OLLAMA_TEST_OK"
                : prompt;

        try {
            AppSettings settings = appSettingsService.getOrCreate();
            ChatClient chatClient = buildChatClient(settings);

            String response = chatClient.prompt()
                    .system("You are an AI health-check assistant. Follow the user's instruction exactly.")
                    .user(effectivePrompt)
                    .call()
                    .content();

            if (response == null) {
                throw new EmailAnalysisException("AI service returned an empty response body.");
            }
            return response.trim();
        } catch (Exception e) {
            log.error("AI model test request failed: {}", e.getMessage());
            throw new EmailAnalysisException("AI model test failed", e);
        }
    }

    private ChatClient buildChatClient(AppSettings settings) {
        Float temperature = settings.getLlmTemperature() == null
                ? 0.3f
                : settings.getLlmTemperature().floatValue();

        OllamaOptions options = OllamaOptions.create()
                .withModel(settings.getLlmModel())
                .withTemperature(temperature);

        return chatClientBuilder
                .defaultOptions(options)
                .build();
    }
}
