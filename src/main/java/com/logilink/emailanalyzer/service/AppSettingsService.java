package com.logilink.emailanalyzer.service;

import com.logilink.emailanalyzer.domain.AppSettings;
import com.logilink.emailanalyzer.exception.EmailAnalysisException;
import com.logilink.emailanalyzer.model.SettingsForm;
import com.logilink.emailanalyzer.repository.AppSettingsRepository;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

@Service
public class AppSettingsService {

    private static final Logger log = LoggerFactory.getLogger(AppSettingsService.class);
    private final AppSettingsRepository repository;
    private final String defaultSchedulerCron;
    private final int defaultSchedulerDateRangeDays;
    private final int defaultSchedulerMaxEmails;

    public AppSettingsService(
            AppSettingsRepository repository,
            @Value("${email.analysis.cron:0 */5 * * * *}") String defaultSchedulerCron,
            @Value("${email.analysis.default-date-range-days:1}") int defaultSchedulerDateRangeDays,
            @Value("${email.analysis.default-max-emails:1000}") int defaultSchedulerMaxEmails
    ) {
        this.repository = repository;
        this.defaultSchedulerCron = defaultSchedulerCron;
        this.defaultSchedulerDateRangeDays = defaultSchedulerDateRangeDays;
        this.defaultSchedulerMaxEmails = defaultSchedulerMaxEmails;
    }

    @Transactional
    public AppSettings getOrCreate() {
        return repository.findById(AppSettings.SINGLETON_ID)
                .orElseGet(this::createDefault);
    }

    @Transactional
    public AppSettings save(SettingsForm form) {
        AppSettings settings = getOrCreate();
        String mailHost = trim(form.getMailHost());
        Integer mailPort = form.getMailPort();
        String mailUsername = trim(form.getMailUsername());
        String mailPassword = form.getMailPassword();
        Boolean mailSslEnabled = form.getMailSslEnabled();
        String systemPrompt = trim(form.getSystemPrompt());
        String llmModel = trim(form.getLlmModel());
        String llmUrl = trim(form.getLlmUrl());
        Double llmTemperature = form.getLlmTemperature();
        Boolean schedulerEnabled = form.getSchedulerEnabled();
        String schedulerCron = trim(form.getSchedulerCron());
        Integer schedulerDateRangeDays = form.getSchedulerDateRangeDays();
        Integer schedulerMaxEmails = form.getSchedulerMaxEmails();

        validateCron(schedulerCron);
        validatePositive("Date range days", schedulerDateRangeDays);
        validatePositive("Max emails", schedulerMaxEmails);

        if (same(settings.getMailHost(), mailHost)
                && same(settings.getMailPort(), mailPort)
                && same(settings.getMailUsername(), mailUsername)
                && same(settings.getMailPassword(), mailPassword)
                && same(settings.getMailSslEnabled(), mailSslEnabled)
                && same(settings.getSystemPrompt(), systemPrompt)
                && same(settings.getLlmModel(), llmModel)
                && same(settings.getLlmUrl(), llmUrl)
                && same(settings.getLlmTemperature(), llmTemperature)
                && same(settings.getSchedulerEnabled(), schedulerEnabled)
                && same(settings.getSchedulerCron(), schedulerCron)
                && same(settings.getSchedulerDateRangeDays(), schedulerDateRangeDays)
                && same(settings.getSchedulerMaxEmails(), schedulerMaxEmails)) {
            return settings;
        }

        settings.setMailHost(mailHost);
        settings.setMailPort(mailPort);
        settings.setMailUsername(mailUsername);
        settings.setMailPassword(mailPassword);
        settings.setMailSslEnabled(mailSslEnabled);
        settings.setSystemPrompt(systemPrompt);

        settings.setLlmModel(llmModel);
        settings.setLlmUrl(llmUrl);
        settings.setLlmTemperature(llmTemperature);
        settings.setSchedulerEnabled(schedulerEnabled);
        settings.setSchedulerCron(schedulerCron);
        settings.setSchedulerDateRangeDays(schedulerDateRangeDays);
        settings.setSchedulerMaxEmails(schedulerMaxEmails);

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

    public boolean testSmtpConnection(String host, Integer port, String user, String password, Boolean sslEnabled) {
        return testSmtpConnectionDetailed(host, port, user, password, sslEnabled).isSuccess();
    }

    public TestEndpointResult testSmtpConnectionDetailed(String host, Integer port, String user, String password, Boolean sslEnabled) {
        if (isBlank(host) || port == null || isBlank(user) || isBlank(password)) {
            return TestEndpointResult.failure("Missing SMTP settings. host, port, username and password are required.");
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
                return TestEndpointResult.success(
                        "SMTP connection successful.",
                        Map.of("smtpHost", smtpHost, "smtpPort", smtpPort)
                );
            }
        } catch (Exception e) {
            log.error("SMTP connection test failed: {}", e.getMessage());
            return TestEndpointResult.failure("SMTP connection failed: " + e.getMessage());
        }
    }

    public boolean testAiChatConnection(String llmUrl, String llmModel, Double llmTemperature) {
        return testAiChatConnectionDetailed(llmUrl, llmModel, llmTemperature).isSuccess();
    }

    public TestEndpointResult testAiChatConnectionDetailed(String llmUrl, String llmModel, Double llmTemperature) {
        if (isBlank(llmUrl) || isBlank(llmModel)) {
            return TestEndpointResult.failure("Missing AI settings. llmUrl and llmModel are required.");
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
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return TestEndpointResult.failure("AI chat test failed with HTTP status " + response.statusCode() + ".");
            }
            if (response.body() == null || response.body().isBlank()) {
                return TestEndpointResult.failure("AI chat test failed: empty response body.");
            }
            if (!response.body().contains("\"response\"")) {
                return TestEndpointResult.failure("AI chat test failed: response does not contain expected 'response' field.");
            }
            return TestEndpointResult.success(
                    "AI chat test successful.",
                    Map.of("statusCode", response.statusCode(), "model", llmModel)
            );
        } catch (Exception e) {
            log.error("AI chat connection test failed: {}", e.getMessage());
            return TestEndpointResult.failure("AI chat test failed: " + e.getMessage());
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
        return isBlank(cron) ? defaultSchedulerCron : cron;
    }

    @Transactional(readOnly = true)
    public int getSchedulerDateRangeDaysOrDefault() {
        Integer dateRangeDays = getOrCreate().getSchedulerDateRangeDays();
        return dateRangeDays == null || dateRangeDays <= 0 ? defaultSchedulerDateRangeDays : dateRangeDays;
    }

    @Transactional(readOnly = true)
    public int getSchedulerMaxEmailsOrDefault() {
        Integer maxEmails = getOrCreate().getSchedulerMaxEmails();
        return maxEmails == null || maxEmails <= 0 ? defaultSchedulerMaxEmails : maxEmails;
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
                .llmModel("llama3.2")
                .llmUrl("http://localhost:11434")
                .llmTemperature(0.3)
                .schedulerEnabled(Boolean.FALSE)
                .schedulerCron(defaultSchedulerCron)
                .schedulerDateRangeDays(defaultSchedulerDateRangeDays)
                .schedulerMaxEmails(defaultSchedulerMaxEmails)
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

    private static void validatePositive(String fieldName, Integer value) {
        if (value != null && value <= 0) {
            throw new EmailAnalysisException(fieldName + " must be greater than 0.");
        }
    }

    private static boolean same(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    public static final class TestEndpointResult {
        private final boolean success;
        private final String message;
        private final Map<String, Object> details;

        private TestEndpointResult(boolean success, String message, Map<String, Object> details) {
            this.success = success;
            this.message = message;
            this.details = details == null ? Map.of() : Map.copyOf(details);
        }

        public static TestEndpointResult success(String message, Map<String, Object> details) {
            return new TestEndpointResult(true, message, details);
        }

        public static TestEndpointResult failure(String message) {
            return new TestEndpointResult(false, message, Map.of());
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public Map<String, Object> toResponseBody() {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", success);
            response.put("message", message);
            response.putAll(details);
            return response;
        }
    }

}
