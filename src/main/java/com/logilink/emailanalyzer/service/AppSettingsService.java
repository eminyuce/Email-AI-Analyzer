package com.logilink.emailanalyzer.service;

import com.logilink.emailanalyzer.domain.AppSettings;
import com.logilink.emailanalyzer.exception.EmailAnalysisException;
import com.logilink.emailanalyzer.model.SettingsForm;
import com.logilink.emailanalyzer.repository.AppSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppSettingsService {

    private final AppSettingsRepository repository;

    @Transactional
    public AppSettings getOrCreate() {
        return repository.findById(AppSettings.SINGLETON_ID)
                .orElseGet(this::createDefault);
    }

    @Transactional
    public AppSettings save(SettingsForm form) {
        AppSettings settings = getOrCreate();
        settings.setMailHost(trim(form.getMailHost()));
        settings.setMailPort(form.getMailPort());
        settings.setMailUsername(trim(form.getMailUsername()));
        settings.setMailPassword(form.getMailPassword());
        settings.setMailSslEnabled(form.getMailSslEnabled());
        settings.setSystemPrompt(trim(form.getSystemPrompt()));
        
        // New settings
        settings.setDbHost(trim(form.getDbHost()));
        settings.setDbPort(form.getDbPort());
        settings.setDbName(trim(form.getDbName()));
        settings.setDbUsername(trim(form.getDbUsername()));
        settings.setDbPassword(form.getDbPassword());
        
        settings.setLlmModel(trim(form.getLlmModel()));
        settings.setLlmUrl(trim(form.getLlmUrl()));
        settings.setLlmTemperature(form.getLlmTemperature());
        
        return repository.save(settings);
    }

    @Transactional(readOnly = true)
    public String getRequiredSystemPrompt() {
        String prompt = getExistingSettings().getSystemPrompt();
        if (prompt == null || prompt.isBlank()) {
            throw new EmailAnalysisException("System prompt is not configured. Please update it on /settings.");
        }
        return prompt;
    }

    @Transactional(readOnly = true)
    public AppSettings getRequiredMailSettings() {
        AppSettings settings = getExistingSettings();
        if (isBlank(settings.getMailHost())
                || settings.getMailPort() == null
                || isBlank(settings.getMailUsername())
                || isBlank(settings.getMailPassword())
                || settings.getMailSslEnabled() == null) {
            throw new EmailAnalysisException("Email server settings are not configured. Please update them on /settings.");
        }
        return settings;
    }

    public boolean testDatabaseConnection(String host, Integer port, String dbName, String user, String password) {
        String url = String.format("jdbc:postgresql://%s:%d/%s", host, port, dbName);
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            return conn.isValid(5);
        } catch (Exception e) {
            log.error("Database connection test failed: {}", e.getMessage());
            return false;
        }
    }

    private AppSettings createDefault() {
        AppSettings defaults = AppSettings.builder()
                .id(AppSettings.SINGLETON_ID)
                .mailHost("")
                .mailPort(993)
                .mailUsername("")
                .mailPassword("")
                .mailSslEnabled(Boolean.TRUE)
                .systemPrompt("")
                .dbHost("localhost")
                .dbPort(5432)
                .dbName("email_db")
                .dbUsername("postgres")
                .dbPassword("password")
                .llmModel("llama3.2")
                .llmUrl("http://localhost:11434")
                .llmTemperature(0.3)
                .build();
        return repository.save(defaults);
    }

    private AppSettings getExistingSettings() {
        return repository.findById(AppSettings.SINGLETON_ID)
                .orElseThrow(() -> new EmailAnalysisException("Settings not found. Please configure /settings first."));
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
