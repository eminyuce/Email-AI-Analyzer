package com.logilink.emailanalyzer.service;

import com.logilink.emailanalyzer.domain.AppSettings;
import com.logilink.emailanalyzer.exception.EmailAnalysisException;
import com.logilink.emailanalyzer.model.EmailAnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AIService {

    private final ChatClient.Builder chatClientBuilder;
    private final AppSettingsService appSettingsService;

    public AIService(ChatClient.Builder builder, AppSettingsService appSettingsService) {
        this.chatClientBuilder = builder;
        this.appSettingsService = appSettingsService;
    }

    public EmailAnalysisResult analyzeEmail(String emailId, String subject, String sender, String content) {
        try {
            AppSettings settings = appSettingsService.getOrCreate();
            String systemPrompt = settings.getSystemPrompt();

            // Dynamic Model Configuration
            String modelName = settings.getLlmModel();
            String baseUrl = settings.getLlmUrl();
            Double temperature = settings.getLlmTemperature();

            OllamaApi ollamaApi = new OllamaApi(baseUrl);
            OllamaChatModel chatModel = new OllamaChatModel(ollamaApi, 
                OllamaOptions.builder()
                    .withModel(modelName)
                    .withTemperature(temperature)
                    .build());

            ChatClient chatClient = chatClientBuilder.build();

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
}
