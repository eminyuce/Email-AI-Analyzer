package com.logilink.emailanalyzer.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
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

    @NotBlank(message = "LLM model is required")
    private String llmModel;

    @NotBlank(message = "LLM URL is required")
    private String llmUrl;

    @NotNull(message = "LLM temperature is required")
    private Double llmTemperature;

    @NotNull(message = "Scheduler enabled is required")
    private Boolean schedulerEnabled;

    @NotBlank(message = "Cron expression is required")
    @Pattern(regexp = "^\\S+\\s+\\S+\\s+\\S+\\s+\\S+\\s+\\S+\\s+\\S+$", message = "Cron must have 6 fields")
    private String schedulerCron;

    @Min(value = 1, message = "Date range days must be at least 1")
    private Integer schedulerDateRangeDays;

    @Min(value = 1, message = "Max emails must be at least 1")
    private Integer schedulerMaxEmails;
}
