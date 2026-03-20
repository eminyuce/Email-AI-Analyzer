package com.logilink.emailanalyzer.model;

import com.logilink.emailanalyzer.domain.AppSettings;
import jakarta.validation.constraints.*;

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

    // LLM Settings
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

    public SettingsForm() {
    }

    public static SettingsForm from(AppSettings settings) {
        SettingsForm form = new SettingsForm();
        form.setMailHost(settings.getMailHost());
        form.setMailPort(settings.getMailPort());
        form.setMailUsername(settings.getMailUsername());
        form.setMailPassword(settings.getMailPassword());
        form.setMailSslEnabled(settings.getMailSslEnabled());
        form.setSystemPrompt(settings.getSystemPrompt());

        form.setLlmModel(settings.getLlmModel());
        form.setLlmUrl(settings.getLlmUrl());
        form.setLlmTemperature(settings.getLlmTemperature());
        form.setSchedulerEnabled(settings.getSchedulerEnabled());
        form.setSchedulerCron(settings.getSchedulerCron());

        return form;
    }

    // Getters and Setters
    public String getMailHost() {
        return mailHost;
    }

    public void setMailHost(String mailHost) {
        this.mailHost = mailHost;
    }

    public Integer getMailPort() {
        return mailPort;
    }

    public void setMailPort(Integer mailPort) {
        this.mailPort = mailPort;
    }

    public String getMailUsername() {
        return mailUsername;
    }

    public void setMailUsername(String mailUsername) {
        this.mailUsername = mailUsername;
    }

    public String getMailPassword() {
        return mailPassword;
    }

    public void setMailPassword(String mailPassword) {
        this.mailPassword = mailPassword;
    }

    public Boolean getMailSslEnabled() {
        return mailSslEnabled;
    }

    public void setMailSslEnabled(Boolean mailSslEnabled) {
        this.mailSslEnabled = mailSslEnabled;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public String getLlmUrl() {
        return llmUrl;
    }

    public void setLlmUrl(String llmUrl) {
        this.llmUrl = llmUrl;
    }

    public Double getLlmTemperature() {
        return llmTemperature;
    }

    public void setLlmTemperature(Double llmTemperature) {
        this.llmTemperature = llmTemperature;
    }

    public Boolean getSchedulerEnabled() {
        return schedulerEnabled;
    }

    public void setSchedulerEnabled(Boolean schedulerEnabled) {
        this.schedulerEnabled = schedulerEnabled;
    }

    public String getSchedulerCron() {
        return schedulerCron;
    }

    public void setSchedulerCron(String schedulerCron) {
        this.schedulerCron = schedulerCron;
    }
}
