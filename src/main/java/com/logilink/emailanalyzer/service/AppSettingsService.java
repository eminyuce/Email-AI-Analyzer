package com.logilink.emailanalyzer.service;

import com.logilink.emailanalyzer.domain.AppSettings;
import com.logilink.emailanalyzer.exception.EmailAnalysisException;
import com.logilink.emailanalyzer.model.SettingsForm;
import com.logilink.emailanalyzer.repository.AppSettingsRepository;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Properties;

@Service
public class AppSettingsService {

    private static final Logger log = LoggerFactory.getLogger(AppSettingsService.class);

    private final AppSettingsRepository repository;

    public AppSettingsService(AppSettingsRepository repository) {
        this.repository = repository;
    }

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
        settings.setSchedulerEnabled(form.getSchedulerEnabled());
        settings.setSchedulerCron(trim(form.getSchedulerCron()));

        validateCron(settings.getSchedulerCron());

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
        String url = String.format("jdbc:mysql://%s:%d/%s", host, port, dbName);
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            return conn.isValid(5);
        } catch (Exception e) {
            log.error("Database connection test failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean testSmtpConnection(String host, Integer port, String user, String password, Boolean sslEnabled) {
        if (isBlank(host) || port == null || isBlank(user) || isBlank(password)) {
            return false;
        }

        String smtpHost = host.startsWith("imap.") ? "smtp." + host.substring("imap.".length()) : host;
        int smtpPort = (port == 993) ? (Boolean.TRUE.equals(sslEnabled) ? 465 : 587) : port;

        try {
            Properties properties = new Properties();
            properties.put("mail.smtp.host", smtpHost);
            properties.put("mail.smtp.port", String.valueOf(smtpPort));
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtp.connectiontimeout", "5000");
            properties.put("mail.smtp.timeout", "5000");

            if (Boolean.TRUE.equals(sslEnabled)) {
                properties.put("mail.smtp.ssl.enable", "true");
            } else {
                properties.put("mail.smtp.starttls.enable", "true");
            }

            Session session = Session.getInstance(properties);
            try (Transport transport = session.getTransport("smtp")) {
                transport.connect(smtpHost, smtpPort, user, password);
                return true;
            }
        } catch (Exception e) {
            log.error("SMTP connection test failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean testAiChatConnection(String llmUrl, String llmModel, Double llmTemperature) {
        if (isBlank(llmUrl) || isBlank(llmModel)) {
            return false;
        }

        String baseUrl = llmUrl.endsWith("/") ? llmUrl.substring(0, llmUrl.length() - 1) : llmUrl;
        double temperature = llmTemperature != null ? llmTemperature : 0.3;
        String payload = "{\"model\":\"" + escapeJson(llmModel) + "\","
                + "\"prompt\":\"Reply with only OK\","
                + "\"stream\":false,"
                + "\"options\":{\"temperature\":" + temperature + "}}";

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/generate"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200
                    && response.statusCode() < 300
                    && response.body() != null
                    && response.body().contains("\"response\"");
        } catch (Exception e) {
            log.error("AI chat connection test failed: {}", e.getMessage());
            return false;
        }
    }

    @Transactional
    public void updateSchedulerEnabled(boolean enabled) {
        AppSettings settings = getOrCreate();
        settings.setSchedulerEnabled(enabled);
        repository.save(settings);
    }

    @Transactional
    public void updateSchedulerCron(String cron) {
        String normalized = trim(cron);
        validateCron(normalized);
        AppSettings settings = getOrCreate();
        settings.setSchedulerCron(normalized);
        repository.save(settings);
    }

    @Transactional(readOnly = true)
    public String getSchedulerCronOrDefault() {
        String cron = getOrCreate().getSchedulerCron();
        return isBlank(cron) ? "0 */5 * * * *" : cron;
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
                .dbPort(3306)
                .dbName("email_db")
                .dbUsername("email_user")
                .dbPassword("password")
                .llmModel("llama3.2")
                .llmUrl("http://localhost:11434")
                .llmTemperature(0.3)
                .schedulerEnabled(Boolean.FALSE)
                .schedulerCron("0 */5 * * * *")
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

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void validateCron(String cron) {
        if (isBlank(cron)) {
            throw new EmailAnalysisException("Cron expression cannot be empty.");
        }
        try {
            CronExpression.parse(cron);
        } catch (Exception ex) {
            throw new EmailAnalysisException("Invalid cron expression. Use 6-field Spring cron format.", ex);
        }
    }
}
