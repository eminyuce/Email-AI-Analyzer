package com.logilink.emailanalyzer.service;

import com.logilink.emailanalyzer.common.AppConstants;
import com.logilink.emailanalyzer.common.LlmProviderType;
import com.logilink.emailanalyzer.domain.AppSettings;
import com.logilink.emailanalyzer.exception.EmailAnalysisException;
import com.logilink.emailanalyzer.model.GroqRequest;
import com.logilink.emailanalyzer.model.SettingsForm;
import com.logilink.emailanalyzer.repository.AppSettingsRepository;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Service
public class AppSettingsService {

    private static final Logger log = LoggerFactory.getLogger(AppSettingsService.class);
    private static final String SMTP_PREFIX = "smtp.";
    private static final String IMAP_PREFIX = "imap.";

    private final AppSettingsRepository repository;
    private final GroqService groqService;
    private final int aiTestConnectTimeoutSeconds;
    private final int aiTestRequestTimeoutSeconds;
    private final int defaultAnalysisDateRangeDays;
    private final int defaultAnalysisMaxEmails;

    @Value("${groq.api.key:}")
    private String groqApiKey;

    public AppSettingsService(
            AppSettingsRepository repository,
            GroqService groqService,
            @Value("${ai.test.connect-timeout-seconds:10}") int aiTestConnectTimeoutSeconds,
            @Value("${ai.test.request-timeout-seconds:75}") int aiTestRequestTimeoutSeconds,
            @Value("${email.analysis.default-date-range-days:1}") int defaultAnalysisDateRangeDays,
            @Value("${email.analysis.default-max-emails:1000}") int defaultAnalysisMaxEmails
    ) {
        this.repository = repository;
        this.groqService = groqService;
        this.aiTestConnectTimeoutSeconds = aiTestConnectTimeoutSeconds;
        this.aiTestRequestTimeoutSeconds = aiTestRequestTimeoutSeconds;
        this.defaultAnalysisDateRangeDays = defaultAnalysisDateRangeDays;
        this.defaultAnalysisMaxEmails = defaultAnalysisMaxEmails;
    }

    @Transactional
    public AppSettings getOrCreate() {
        return getActiveOrCreate();
    }

    @Transactional(readOnly = true)
    public List<AppSettings> listAll() {
        return repository.findAllByOrderByActiveDescUpdatedAtDesc();
    }

    @Transactional(readOnly = true)
    public AppSettings getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EmailAnalysisException("Settings profile not found: " + id));
    }

    @Transactional
    public AppSettings createProfileFromActive() {
        AppSettings active = getActiveOrCreate();
        AppSettings copy = cloneAsInactiveWithoutId(active);
        return repository.save(copy);
    }

    private AppSettings cloneAsInactiveWithoutId(AppSettings source) {
        AppSettings copy = AppSettings.builder()
                .mailHost(source.getMailHost())
                .mailPort(source.getMailPort())
                .mailUsername(source.getMailUsername())
                .mailPassword(source.getMailPassword())
                .mailSslEnabled(source.getMailSslEnabled())
                .systemPrompt(source.getSystemPrompt())
                .dbHost(source.getDbHost())
                .dbPort(source.getDbPort())
                .dbName(source.getDbName())
                .dbUsername(source.getDbUsername())
                .dbPassword(source.getDbPassword())
                .llmProvider(normalizeLlmProvider(source.getLlmProvider()))
                .llmModel(source.getLlmModel())
                .llmUrl(source.getLlmUrl())
                .llmTemperature(source.getLlmTemperature())
                .schedulerEnabled(Boolean.FALSE)
                .schedulerCron("")
                .schedulerDateRangeDays(source.getSchedulerDateRangeDays())
                .schedulerMaxEmails(source.getSchedulerMaxEmails())
                .active(Boolean.FALSE)
                .build();
        // Duplicate must always be a new non-active row.
        copy.setId(null);
        copy.setActive(Boolean.FALSE);
        return copy;
    }

    @Transactional
    public AppSettings createNewProfile() {
        AppSettings created = AppSettings.builder()
                .mailHost("")
                .mailPort(null)
                .mailUsername("")
                .mailPassword("")
                .mailSslEnabled(null)
                .systemPrompt("")
                .llmProvider(LlmProviderType.OLLAMA.toSettingsValue())
                .llmModel("")
                .llmUrl("")
                .llmTemperature(null)
                .schedulerEnabled(Boolean.FALSE)
                .schedulerCron("")
                .schedulerDateRangeDays(defaultAnalysisDateRangeDays)
                .schedulerMaxEmails(defaultAnalysisMaxEmails)
                .active(Boolean.FALSE)
                .build();
        return repository.save(created);
    }

    @Transactional
    public AppSettings createNewProfile(SettingsForm form) {
        AppSettings created = AppSettings.builder()
                .active(Boolean.FALSE)
                .build();
        return saveToProfile(created, form);
    }

    @Transactional
    public void activateProfile(Long id) {
        AppSettings target = getById(id);
        repository.deactivateAll();
        target.setActive(Boolean.TRUE);
        repository.save(target);
    }

    @Transactional
    public AppSettings save(SettingsForm form) {
        AppSettings settings = getActiveOrCreate();
        return saveToProfile(settings, form);
    }

    @Transactional
    public AppSettings saveToProfile(Long profileId, SettingsForm form) {
        AppSettings settings = getById(profileId);
        return saveToProfile(settings, form);
    }

    private AppSettings saveToProfile(AppSettings settings, SettingsForm form) {
        String mailHost = trim(form.getMailHost());
        Integer mailPort = form.getMailPort();
        String mailUsername = trim(form.getMailUsername());
        String mailPassword = form.getMailPassword();
        Boolean mailSslEnabled = form.getMailSslEnabled();
        String systemPrompt = trim(form.getSystemPrompt());
        String llmProvider = normalizeLlmProvider(form.getLlmProvider());
        String llmModel = trim(form.getLlmModel());
        String llmUrl = trim(form.getLlmUrl());
        Double llmTemperature = form.getLlmTemperature();
        Integer schedulerDateRangeDays = form.getSchedulerDateRangeDays();
        Integer schedulerMaxEmails = form.getSchedulerMaxEmails();
        Boolean active = form.getActive();

        validateRequiredPositive("Date range days", schedulerDateRangeDays);
        validateRequiredPositive("Max emails", schedulerMaxEmails);
        validateProfileStatusChange(settings, active);

        if (same(settings.getMailHost(), mailHost)
                && same(settings.getMailPort(), mailPort)
                && same(settings.getMailUsername(), mailUsername)
                && same(settings.getMailPassword(), mailPassword)
                && same(settings.getMailSslEnabled(), mailSslEnabled)
                && same(settings.getSystemPrompt(), systemPrompt)
                && same(settings.getLlmProvider(), llmProvider)
                && same(settings.getLlmModel(), llmModel)
                && same(settings.getLlmUrl(), llmUrl)
                && same(settings.getLlmTemperature(), llmTemperature)
                && same(settings.getSchedulerDateRangeDays(), schedulerDateRangeDays)
                && same(settings.getSchedulerMaxEmails(), schedulerMaxEmails)
                && same(settings.getActive(), active)) {
            return settings;
        }

        settings.setMailHost(mailHost);
        settings.setMailPort(mailPort);
        settings.setMailUsername(mailUsername);
        settings.setMailPassword(mailPassword);
        settings.setMailSslEnabled(mailSslEnabled);
        settings.setSystemPrompt(systemPrompt);

        settings.setLlmProvider(llmProvider);
        settings.setLlmModel(llmModel);
        settings.setLlmUrl(llmUrl);
        settings.setLlmTemperature(llmTemperature);
        settings.setSchedulerDateRangeDays(schedulerDateRangeDays);
        settings.setSchedulerMaxEmails(schedulerMaxEmails);
        applyProfileActiveState(settings, active);

        return repository.save(settings);
    }

    @Transactional(readOnly = true)
    public String getRequiredSystemPrompt() {
        String prompt = getActiveRequiredSettings().getSystemPrompt();
        if (StringUtils.isBlank(prompt)) {
            throw new EmailAnalysisException(AppConstants.Messages.SYSTEM_PROMPT_MISSING);
        }
        return prompt;
    }

    @Transactional(readOnly = true)
    public AppSettings getRequiredMailSettings() {
        AppSettings settings = getActiveRequiredSettings();
        if (isBlank(settings.getMailHost())
                || settings.getMailPort() == null
                || isBlank(settings.getMailUsername())
                || isBlank(settings.getMailPassword())
                || settings.getMailSslEnabled() == null) {
            throw new EmailAnalysisException(AppConstants.Messages.EMAIL_SETTINGS_MISSING);
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

        String smtpHost = host.startsWith(IMAP_PREFIX)
                ? SMTP_PREFIX + host.substring(IMAP_PREFIX.length())
                : host;
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

    public boolean testAiChatConnection(String llmProvider, String llmUrl, String llmModel, Double llmTemperature) {
        return testAiChatConnectionDetailed(llmProvider, llmUrl, llmModel, llmTemperature).isSuccess();
    }

    public TestEndpointResult testAiChatConnectionDetailed(String llmProvider, String llmUrl, String llmModel, Double llmTemperature) {
        if (LlmProviderType.fromSettingsValue(llmProvider) == LlmProviderType.GROQ) {
            return testGroqChatConnectionDetailed(llmModel, llmTemperature);
        }
        return testOllamaChatConnectionDetailed(llmUrl, llmModel, llmTemperature);
    }

    private TestEndpointResult testOllamaChatConnectionDetailed(String llmUrl, String llmModel, Double llmTemperature) {
        if (isBlank(llmUrl) || isBlank(llmModel)) {
            return TestEndpointResult.failure("Missing AI settings. llmUrl and llmModel are required for Ollama.");
        }

        String baseUrl = llmUrl.endsWith("/") ? llmUrl.substring(0, llmUrl.length() - 1) : llmUrl;
        double temperature = llmTemperature != null ? llmTemperature : 0.3;
        String payload = "{\"model\":\"" + escapeJson(llmModel) + "\","
                + "\"prompt\":\"Reply with only OK\","
                + "\"stream\":false,"
                + "\"options\":{\"temperature\":" + temperature + ",\"num_predict\":8}}";

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(aiTestConnectTimeoutSeconds))
                    .build();

            long startedAt = System.currentTimeMillis();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/generate"))
                    .timeout(Duration.ofSeconds(aiTestRequestTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long durationMs = System.currentTimeMillis() - startedAt;
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
                    Map.of("statusCode", response.statusCode(), "model", llmModel, "durationMs", durationMs)
            );
        } catch (Exception e) {
            log.error("AI chat connection test failed: {}", e.getMessage());
            return TestEndpointResult.failure("AI chat test failed: " + e.getMessage());
        }
    }

    private TestEndpointResult testGroqChatConnectionDetailed(String llmModel, Double llmTemperature) {
        if (isBlank(llmModel)) {
            return TestEndpointResult.failure("Missing AI settings. llmModel is required for Groq.");
        }
        if (isBlank(groqApiKey)) {
            return TestEndpointResult.failure("Groq API key is not configured. Set GROQ_API_KEY or groq.api.key.");
        }
        double temperature = llmTemperature != null ? llmTemperature : 0.3;
        try {
            long startedAt = System.currentTimeMillis();
            String reply = groqService.chat(
                    llmModel,
                    List.of(new GroqRequest.Message("user", "Reply with only OK")),
                    temperature,
                    32
            );
            long durationMs = System.currentTimeMillis() - startedAt;
            if (reply == null || !reply.toLowerCase().contains("ok")) {
                return TestEndpointResult.failure("Groq test unexpected response: " + reply);
            }
            return TestEndpointResult.success(
                    "Groq chat test successful.",
                    Map.of("model", llmModel, "durationMs", durationMs)
            );
        } catch (Exception e) {
            log.error("Groq connection test failed: {}", e.getMessage());
            return TestEndpointResult.failure("Groq chat test failed: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public int getRequiredSchedulerDateRangeDays() {
        Integer dateRangeDays = getActiveRequiredSettings().getSchedulerDateRangeDays();
        if (dateRangeDays == null || dateRangeDays <= 0) {
            throw new EmailAnalysisException("Scheduler date range days is missing in settings.");
        }
        return dateRangeDays;
    }

    @Transactional(readOnly = true)
    public int getRequiredSchedulerMaxEmails() {
        Integer maxEmails = getActiveRequiredSettings().getSchedulerMaxEmails();
        if (maxEmails == null || maxEmails <= 0) {
            throw new EmailAnalysisException("Scheduler max emails is missing in settings.");
        }
        return maxEmails;
    }

    private AppSettings createDefault() {
        AppSettings defaults = AppSettings.builder()
                .mailHost("")
                .mailPort(null)
                .mailUsername("")
                .mailPassword("")
                .mailSslEnabled(null)
                .systemPrompt("")
                .llmProvider(LlmProviderType.OLLAMA.toSettingsValue())
                .llmModel("")
                .llmUrl("")
                .llmTemperature(null)
                .schedulerEnabled(Boolean.FALSE)
                .schedulerCron("")
                .schedulerDateRangeDays(defaultAnalysisDateRangeDays)
                .schedulerMaxEmails(defaultAnalysisMaxEmails)
                .active(Boolean.TRUE)
                .build();
        return repository.save(defaults);
    }

    private AppSettings getActiveOrCreate() {
        return repository.findFirstByActiveTrue()
                .orElseGet(() -> {
                    List<AppSettings> existing = repository.findAll(Sort.by(Sort.Direction.ASC, "id"));
                    if (existing.isEmpty()) {
                        return createDefault();
                    }
                    AppSettings first = existing.get(0);
                    first.setActive(Boolean.TRUE);
                    repository.save(first);
                    if (existing.size() > 1) {
                        log.warn("No active settings profile found. Marked id={} as active.", first.getId());
                    }
                    return first;
                });
    }

    private AppSettings getActiveRequiredSettings() {
        AppSettings active = repository.findFirstByActiveTrue()
                .orElseThrow(() -> new EmailAnalysisException(AppConstants.Messages.SETTINGS_NOT_FOUND));
        if (active.getId() == null) {
            throw new EmailAnalysisException(AppConstants.Messages.SETTINGS_NOT_FOUND);
        }
        return active;
    }

    private static String trim(String value) {
        return StringUtils.trimToNull(value);
    }

    private static String normalizeLlmProvider(String value) {
        return LlmProviderType.fromSettingsValue(value).toSettingsValue();
    }

    private static boolean isBlank(String value) {
        return StringUtils.isBlank(value);
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void validateRequiredPositive(String fieldName, Integer value) {
        if (value == null || value <= 0) {
            throw new EmailAnalysisException(fieldName + " must be a positive number.");
        }
    }

    private void validateProfileStatusChange(AppSettings settings, Boolean targetActive) {
        if (!Boolean.FALSE.equals(targetActive) || !Boolean.TRUE.equals(settings.getActive())) {
            return;
        }
        if (!repository.existsByActiveTrueAndIdNot(settings.getId())) {
            throw new EmailAnalysisException("At least one settings profile must remain Active.");
        }
    }

    private void applyProfileActiveState(AppSettings settings, Boolean targetActive) {
        if (!Boolean.TRUE.equals(targetActive)) {
            settings.setActive(Boolean.FALSE);
            return;
        }
        if (!Boolean.TRUE.equals(settings.getActive())) {
            repository.deactivateAll();
        }
        settings.setActive(Boolean.TRUE);
    }

    private static boolean same(Object left, Object right) {
        return ObjectUtils.equals(left, right);
    }

    public static final class TestEndpointResult {
        private final boolean success;
        private final String message;
        private final Map<String, Object> details;

        private TestEndpointResult(boolean success, String message, Map<String, Object> details) {
            this.success = success;
            this.message = message;
            this.details = MapUtils.isEmpty(details) ? Map.of() : Map.copyOf(details);
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

        public Map<String, Object> getDetails() {
            return details;
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
