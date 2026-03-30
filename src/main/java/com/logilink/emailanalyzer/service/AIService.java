package com.logilink.emailanalyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logilink.emailanalyzer.common.AppConstants;
import com.logilink.emailanalyzer.common.LlmProviderType;
import com.logilink.emailanalyzer.config.AppSecretsDebugProperties;
import com.logilink.emailanalyzer.domain.AppSettings;
import com.logilink.emailanalyzer.exception.EmailAnalysisException;
import com.logilink.emailanalyzer.model.EmailAnalysisResult;
import com.logilink.emailanalyzer.model.FetchedEmailDto;
import com.logilink.emailanalyzer.model.GroqRequest;
import com.logilink.emailanalyzer.util.LlmJsonUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);

    private final AppSettingsService appSettingsService;
    private final GroqService groqService;
    private final ObjectMapper objectMapper;
    private final AppSecretsDebugProperties secretsDebug;

    @Value("${groq.api.key:}")
    private String groqApiKey;


    public AIService(
            AppSettingsService appSettingsService,
            GroqService groqService,
            ObjectMapper objectMapper,
            AppSecretsDebugProperties secretsDebug
    ) {
        this.appSettingsService = appSettingsService;
        this.groqService = groqService;
        this.objectMapper = objectMapper;
        this.secretsDebug = secretsDebug;
    }

    /**
     * Runs LLM analysis for one email using the active profile’s provider (Groq or Ollama) and
     * returns structured {@link EmailAnalysisResult} JSON from the model.
     */
    public EmailAnalysisResult analyzeEmail(String emailId, String subject, String sender, String content,
                                            String inReplyTo, String references, List<FetchedEmailDto.AttachmentDto> attachments) {
        return analyzeEmail(emailId, subject, sender, content, inReplyTo, references, attachments, null);
    }

    /**
     * Same as {@link #analyzeEmail(String, String, String, String, String, String, List)} but allows a
     * one-off system prompt override (non-blank) instead of the profile value.
     */
    public EmailAnalysisResult analyzeEmail(String emailId, String subject, String sender, String content,
                                            String inReplyTo, String references, List<FetchedEmailDto.AttachmentDto> attachments,
                                            String systemPromptOverride) {
        try {
            AppSettings settings = appSettingsService.getOrCreate();
            LlmProviderType provider = LlmProviderType.fromSettingsValue(settings.getLlmProvider());
            String systemPrompt = StringUtils.isNotBlank(systemPromptOverride)
                    ? systemPromptOverride
                    : settings.getSystemPrompt();
            log.debug("AI system prompt: {}", systemPrompt);

            StringBuilder userPromptBuilder = new StringBuilder();
            userPromptBuilder.append(String.format("Email ID: %s\n", emailId));
            userPromptBuilder.append(String.format("Subject: %s\n", subject));
            userPromptBuilder.append(String.format("Sender: %s\n", sender));
            if (StringUtils.isNotBlank(inReplyTo)) {
                userPromptBuilder.append(String.format("In-Reply-To: %s\n", inReplyTo));
            }
            if (StringUtils.isNotBlank(references)) {
                userPromptBuilder.append(String.format("References: %s\n", references));
            }
            userPromptBuilder.append("Content:\n").append(content);

            if (CollectionUtils.isNotEmpty(attachments)) {
                userPromptBuilder.append("\n\nAttachments:\n");
                for (int i = 0; i < attachments.size(); i++) {
                    FetchedEmailDto.AttachmentDto attachment = attachments.get(i);
                    userPromptBuilder.append(String.format("  Attachment %d: File Name: '%s', Content Type: '%s', Size: %d bytes\n",
                            i + 1, attachment.getFileName(), attachment.getContentType(), attachment.getSize()));
                }
            }

            String userPrompt = userPromptBuilder.toString();
            log.debug("AI user prompt: {}", userPrompt);

            EmailAnalysisResult result = switch (provider) {
                case GROQ -> analyzeWithGroq(settings, systemPrompt, userPrompt);
                case OLLAMA -> analyzeWithOllama(settings, systemPrompt, userPrompt);
            };
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

    private EmailAnalysisResult analyzeWithOllama(AppSettings settings, String systemPrompt, String userPrompt) {
        ChatClient chatClient = createOllamaChatClient(settings);
        String response = chatClient.prompt()
                .system(spec -> spec.text("{systemPrompt}").params(Map.of("systemPrompt", systemPrompt)))
                .user(spec -> spec.text("{userPrompt}").params(Map.of("userPrompt", userPrompt)))
                .call()
                .content();

        EmailAnalysisResult result = new EmailAnalysisResult();
        try {
            result = objectMapper.readValue(LlmJsonUtils.cleanJson(response), EmailAnalysisResult.class);
        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", response);
        }
        return result;
    }

    private EmailAnalysisResult analyzeWithGroq(AppSettings settings, String systemPrompt, String userPrompt) {
        if (StringUtils.isBlank(groqApiKey)) {
            throw new EmailAnalysisException(
                    "Groq is selected but the API key is not configured. Set environment variable GROQ_API_KEY or groq.api.key."
            );
        }
        if (secretsDebug.isDebugLogSecrets()) {
            log.debug("DEBUG_LOG_SECRETS: groq.api.key=[{}]", groqApiKey);
        }
        if (StringUtils.isBlank(settings.getLlmModel())) {
            throw new EmailAnalysisException("LLM model is not configured in application settings.");
        }
        float temperature = settings.getLlmTemperature() == null
                ? 0.3f
                : settings.getLlmTemperature().floatValue();

        String augmentedSystem = systemPrompt + "\n\n" + AppConstants.AiPrompts.GROQ_JSON_DISCIPLINE;
        List<GroqRequest.Message> messages = List.of(
                new GroqRequest.Message("system", augmentedSystem),
                new GroqRequest.Message("user", userPrompt)
        );
        String raw = groqService.chat(settings.getLlmModel(), messages, (double) temperature);
        String json = LlmJsonUtils.unwrapModelJsonPayload(raw);
        try {
            return objectMapper.readValue(json, EmailAnalysisResult.class);
        } catch (Exception e) {
            throw new EmailAnalysisException("Could not parse Groq response as EmailAnalysisResult JSON: " + e.getMessage(), e);
        }
    }

    private ChatClient createOllamaChatClient(AppSettings settings) {
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
