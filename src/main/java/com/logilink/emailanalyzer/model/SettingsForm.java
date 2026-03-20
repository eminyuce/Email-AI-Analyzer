package com.logilink.emailanalyzer.model;

import com.logilink.emailanalyzer.domain.AppSettings;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    // DB Settings
    @NotBlank(message = "DB host is required")
    private String dbHost;

    @NotNull(message = "DB port is required")
    private Integer dbPort;

    @NotBlank(message = "DB name is required")
    private String dbName;

    @NotBlank(message = "DB username is required")
    private String dbUsername;

    @NotBlank(message = "DB password is required")
    private String dbPassword;

    // LLM Settings
    @NotBlank(message = "LLM model is required")
    private String llmModel;

    @NotBlank(message = "LLM URL is required")
    private String llmUrl;

    @NotNull(message = "LLM temperature is required")
    private Double llmTemperature;

    public static SettingsForm from(AppSettings settings) {
        SettingsForm form = new SettingsForm();
        form.setMailHost(settings.getMailHost());
        form.setMailPort(settings.getMailPort());
        form.setMailUsername(settings.getMailUsername());
        form.setMailPassword(settings.getMailPassword());
        form.setMailSslEnabled(settings.getMailSslEnabled());
        form.setSystemPrompt(settings.getSystemPrompt());
        
        form.setDbHost(settings.getDbHost());
        form.setDbPort(settings.getDbPort());
        form.setDbName(settings.getDbName());
        form.setDbUsername(settings.getDbUsername());
        form.setDbPassword(settings.getDbPassword());
        
        form.setLlmModel(settings.getLlmModel());
        form.setLlmUrl(settings.getLlmUrl());
        form.setLlmTemperature(settings.getLlmTemperature());
        
        return form;
    }
}
