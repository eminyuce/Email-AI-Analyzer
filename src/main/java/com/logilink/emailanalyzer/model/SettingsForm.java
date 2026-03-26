package com.logilink.emailanalyzer.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class SettingsForm {

    @NotBlank(message = "Mail host is required")
    private String mailHost;

    @NotNull(message = "Mail port is required")
    @Min(value = 1, message = "Mail port must be between 1 and 65535")
    @Max(value = 65535, message = "Mail port must be between 1 and 65535")
    private Integer mailPort;

    @NotBlank(message = "Mail username is required")
    private String mailUsername;

    @NotBlank(message = "Mail password is required")
    private String mailPassword;

    @NotNull(message = "SSL option is required")
    private Boolean mailSslEnabled;

    @NotBlank(message = "System prompt is required")
    private String systemPrompt;

    @NotBlank(message = "LLM provider is required")
    @Pattern(regexp = "(?i)ollama|groq", message = "LLM provider must be ollama or groq")
    private String llmProvider = "ollama";

    @NotBlank(message = "LLM model is required")
    private String llmModel;

    private String llmUrl;

    @NotNull(message = "LLM temperature is required")
    private Double llmTemperature;

    @NotNull(message = "Date range days is required")
    @Min(value = 1, message = "Date range days must be at least 1")
    private Integer schedulerDateRangeDays;

    @NotNull(message = "Max emails is required")
    @Min(value = 1, message = "Max emails must be at least 1")
    private Integer schedulerMaxEmails;

    @NotNull(message = "Profile status is required")
    private Boolean active;

    @AssertTrue(message = "LLM URL is required when using Ollama")
    public boolean isLlmUrlValidForProvider() {
        if (llmProvider == null) {
            return true;
        }
        if ("groq".equalsIgnoreCase(llmProvider)) {
            return true;
        }
        return llmUrl != null && !llmUrl.isBlank();
    }
}
