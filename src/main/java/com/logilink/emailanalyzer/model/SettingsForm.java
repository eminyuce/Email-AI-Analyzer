package com.logilink.emailanalyzer.model;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class SettingsForm {

    @NotBlank(message = "Missing required field: mailHost")
    private String mailHost;

    @NotNull(message = "Missing required field: mailPort")
    @Min(value = 1, message = "Mail port must be between 1 and 65535")
    @Max(value = 65535, message = "Mail port must be between 1 and 65535")
    private Integer mailPort;

    @NotBlank(message = "Missing required field: mailUsername")
    private String mailUsername;

    @NotBlank(message = "Missing required field: mailPassword")
    private String mailPassword;

    @NotNull(message = "Missing required field: mailSslEnabled")
    private Boolean mailSslEnabled;

    @NotBlank(message = "Missing required field: systemPrompt")
    private String systemPrompt;

    @NotBlank(message = "Missing required field: llmProvider")
    @Pattern(regexp = "(?i)ollama|groq", message = "LLM provider must be ollama or groq")
    private String llmProvider = "ollama";

    @NotBlank(message = "Missing required field: llmModel")
    private String llmModel;

    private String llmUrl;

    @NotNull(message = "Missing required field: llmTemperature")
    private Double llmTemperature;

    @NotNull(message = "Missing required field: schedulerDateRangeDays")
    @Min(value = 1, message = "Date range days must be at least 1")
    private Integer schedulerDateRangeDays;

    @NotNull(message = "Missing required field: schedulerMaxEmails")
    @Min(value = 1, message = "Max emails must be at least 1")
    private Integer schedulerMaxEmails;

    @NotNull(message = "Missing required field: active")
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
