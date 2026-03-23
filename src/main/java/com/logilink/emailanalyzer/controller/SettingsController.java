package com.logilink.emailanalyzer.controller;

import com.logilink.emailanalyzer.domain.AppSettings;
import com.logilink.emailanalyzer.domain.EmailAnalysis;
import com.logilink.emailanalyzer.mapper.AppSettingsMapper;
import com.logilink.emailanalyzer.mapper.EmailAnalysisMapper;
import com.logilink.emailanalyzer.model.ApiValidationError;
import com.logilink.emailanalyzer.model.AiHealthResponse;
import com.logilink.emailanalyzer.model.ConnectionTestResponse;
import com.logilink.emailanalyzer.model.DefaultSettingsTestResponse;
import com.logilink.emailanalyzer.model.EmailAnalysisReportDto;
import com.logilink.emailanalyzer.model.EmailSubjectDto;
import com.logilink.emailanalyzer.model.FetchedEmailDto;
import com.logilink.emailanalyzer.model.FetchEmailSubjectsResponse;
import com.logilink.emailanalyzer.model.SchedulerControlResponse;
import com.logilink.emailanalyzer.model.SettingsSaveResponse;
import com.logilink.emailanalyzer.model.SettingsForm;
import com.logilink.emailanalyzer.scheduler.EmailScheduler;
import com.logilink.emailanalyzer.service.AIService;
import com.logilink.emailanalyzer.service.AnalysisService;
import com.logilink.emailanalyzer.service.AppSettingsService;
import com.logilink.emailanalyzer.service.EmailService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.ClassPathResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/settings")
public class SettingsController {

    private static final String DEFAULT_MAIL_HOST = "imap.gmail.com";
    private static final int DEFAULT_MAIL_PORT = 993;
    private static final String DEFAULT_MAIL_USERNAME = "logilinklojistik@gmail.com";
    private static final boolean DEFAULT_MAIL_SSL_ENABLED = true;
    private static final String DEFAULT_LLM_URL = "http://localhost:11434";
    private static final double DEFAULT_LLM_TEMPERATURE = 0.3;
    private static final String DEFAULT_SYSTEM_PROMPT_PATH = "system-prompt.txt";

    private final AppSettingsService appSettingsService;
    private final EmailScheduler emailScheduler;
    private final AnalysisService analysisService;
    private final EmailService emailService;
    private final AIService aiService;
    private final AppSettingsMapper appSettingsMapper;
    private final EmailAnalysisMapper emailAnalysisMapper;

    public SettingsController(
            AppSettingsService appSettingsService,
            EmailScheduler emailScheduler,
            AnalysisService analysisService,
            EmailService emailService,
            AIService aiService,
            AppSettingsMapper appSettingsMapper,
            EmailAnalysisMapper emailAnalysisMapper
    ) {
        this.appSettingsService = appSettingsService;
        this.emailScheduler = emailScheduler;
        this.analysisService = analysisService;
        this.emailService = emailService;
        this.aiService = aiService;
        this.appSettingsMapper = appSettingsMapper;
        this.emailAnalysisMapper = emailAnalysisMapper;
    }

    @GetMapping
    public String settings(Model model) {
        if (!model.containsAttribute("settingsForm")) {
            SettingsForm form = appSettingsMapper.toSettingsForm(appSettingsService.getOrCreate());
            if (form.getSchedulerDateRangeDays() == null) {
                form.setSchedulerDateRangeDays(appSettingsService.getSchedulerDateRangeDaysOrDefault());
            }
            if (form.getSchedulerMaxEmails() == null) {
                form.setSchedulerMaxEmails(appSettingsService.getSchedulerMaxEmailsOrDefault());
            }
            model.addAttribute("settingsForm", form);
        }
        model.addAttribute("schedulerRunning", emailScheduler.isRunning());
        return "settings/form";
    }

    @PostMapping
    public String saveSettings(
            @Valid @ModelAttribute("settingsForm") SettingsForm settingsForm,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.settingsForm", bindingResult);
            redirectAttributes.addFlashAttribute("settingsForm", settingsForm);
            return "redirect:/settings";
        }

        applySettings(settingsForm);
        redirectAttributes.addFlashAttribute("saved", true);
        return "redirect:/settings";
    }

    /**
     * Persists test settings used by core-test endpoints and scheduler execution.
     * This endpoint allows API clients to configure mail + LLM settings without using the UI form.
     */
    @PostMapping(path = "/api/core-test/settings", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public ResponseEntity<SettingsSaveResponse> saveSettingsApi(
            @Valid @RequestBody SettingsForm settingsForm,
            BindingResult bindingResult
    ) {
        SettingsSaveResponse response = new SettingsSaveResponse();
        if (bindingResult.hasErrors()) {
            response.setSuccess(false);
            response.setMessage("Validation failed for SettingsForm.");
            response.setErrors(validationErrors(bindingResult));
            response.setSchedulerRunning(emailScheduler.isRunning());
            return ResponseEntity.badRequest().body(response);
        }

        applySettings(settingsForm);
        response.setSuccess(true);
        response.setMessage("Settings saved successfully.");
        response.setSchedulerRunning(emailScheduler.isRunning());
        return ResponseEntity.ok(response);
    }
    /**
     * Runs the end-to-end core flow with default host/user values and a caller-provided
     * model/password/date range. Intended for integration verification of fetch + AI analysis together.
     */
    @GetMapping(path = "/api/core-test/run", produces = "application/json")
    @ResponseBody
    public ResponseEntity<DefaultSettingsTestResponse> saveDefaultSettingsApi(
            @RequestParam("modelName") String modelName,
            @RequestParam("mailPassword") String mailPassword,
            @RequestParam("emailCount") Integer emailCount,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        DefaultSettingsTestResponse response = new DefaultSettingsTestResponse();
        if (modelName == null || modelName.isBlank() || mailPassword == null || mailPassword.isBlank()) {
            response.setSuccess(false);
            response.setMessage("Both modelName and mailPassword are required.");
            response.setSchedulerCronChanged(false);
            return ResponseEntity.badRequest().body(response);
        }
        if (emailCount == null || emailCount <= 0) {
            response.setSuccess(false);
            response.setMessage("emailCount must be greater than 0.");
            response.setSchedulerCronChanged(false);
            return ResponseEntity.badRequest().body(response);
        }
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            response.setSuccess(false);
            response.setMessage("Valid startDate and endDate are required, and startDate must be before endDate.");
            response.setSchedulerCronChanged(false);
            return ResponseEntity.badRequest().body(response);
        }

        SettingsForm settingsForm = new SettingsForm();
        settingsForm.setMailHost(DEFAULT_MAIL_HOST);
        settingsForm.setMailPort(DEFAULT_MAIL_PORT);
        settingsForm.setMailUsername(DEFAULT_MAIL_USERNAME);
        settingsForm.setMailPassword(mailPassword);
        settingsForm.setMailSslEnabled(DEFAULT_MAIL_SSL_ENABLED);
        settingsForm.setSystemPrompt(loadDefaultSystemPrompt());
        settingsForm.setLlmModel(modelName);
        settingsForm.setLlmUrl(DEFAULT_LLM_URL);
        settingsForm.setLlmTemperature(DEFAULT_LLM_TEMPERATURE);
        AppSettings currentSettings = appSettingsService.getOrCreate();
        settingsForm.setSchedulerEnabled(currentSettings.getSchedulerEnabled());
        settingsForm.setSchedulerCron(appSettingsService.getSchedulerCronOrDefault());
        settingsForm.setSchedulerDateRangeDays(appSettingsService.getSchedulerDateRangeDaysOrDefault());
        settingsForm.setSchedulerMaxEmails(appSettingsService.getSchedulerMaxEmailsOrDefault());

        try {
            applySettings(settingsForm);
            Date rangeStart = Date.from(startDate.atZone(ZoneId.systemDefault()).toInstant());
            Date rangeEnd = Date.from(endDate.atZone(ZoneId.systemDefault()).toInstant());
            List<EmailAnalysis> analyzedEmails = analysisService.processEmails(emailCount, rangeStart, rangeEnd);
            List<EmailAnalysisReportDto> reports = new ArrayList<>();
            for (EmailAnalysis analyzedEmail : analyzedEmails) {
                reports.add(emailAnalysisMapper.toReportDto(analyzedEmail));
            }

            response.setSuccess(true);
            response.setMessage("Default settings saved and core email AI flow executed.");
            response.setSchedulerRunning(emailScheduler.isRunning());
            response.setMailUsername(DEFAULT_MAIL_USERNAME);
            response.setLlmModel(modelName);
            response.setSchedulerCronChanged(false);
            response.setRequestedEmailCount(emailCount);
            response.setAnalyzedEmailCount(reports.size());
            response.setRangeStart(startDate);
            response.setRangeEnd(endDate);
            response.setAnalyzedEmailReports(reports);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            response.setSuccess(false);
            response.setMessage("Core email AI flow failed: " + e.getMessage());
            response.setSchedulerCronChanged(false);
            response.setRequestedEmailCount(emailCount);
            response.setAnalyzedEmailCount(0);
            response.setRangeStart(startDate);
            response.setRangeEnd(endDate);
            response.setAnalyzedEmailReports(new ArrayList<>());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/test-smtp")
    @ResponseBody
    public ResponseEntity<ConnectionTestResponse> testSmtp(@ModelAttribute SettingsForm form) {
        AppSettingsService.TestEndpointResult result = appSettingsService.testSmtpConnectionDetailed(
                form.getMailHost(),
                form.getMailPort(),
                form.getMailUsername(),
                form.getMailPassword(),
                form.getMailSslEnabled()
        );
        HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(toConnectionTestResponse(result));
    }

    @PostMapping("/test-ai")
    @ResponseBody
    public ResponseEntity<ConnectionTestResponse> testAi(@ModelAttribute SettingsForm form) {
        AppSettingsService.TestEndpointResult result = appSettingsService.testAiChatConnectionDetailed(
                form.getLlmUrl(),
                form.getLlmModel(),
                form.getLlmTemperature()
        );
        HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(toConnectionTestResponse(result));
    }

    /**
     * Validates only the Ollama model response path used by analysis jobs.
     * It does not fetch emails or run scheduler logic; purpose is isolated LLM service health-check.
     */
    @GetMapping(path = "/api/core-test/ai-health", produces = "application/json")
    @ResponseBody
    public ResponseEntity<AiHealthResponse> testAiHealth(
            @RequestParam(value = "prompt", required = false) String prompt
    ) {
        String expectedToken = "OLLAMA_TEST_OK";
        long startedAt = System.currentTimeMillis();
        try {
            String response = aiService.testModelResponse(prompt);
            long durationMs = System.currentTimeMillis() - startedAt;
            boolean tokenMatched = response.contains(expectedToken);

            AiHealthResponse body = new AiHealthResponse();
            body.setSuccess(true);
            body.setMessage("Ollama model responded to health-check request.");
            body.setExpectedToken(expectedToken);
            body.setContainsExpectedToken(tokenMatched);
            body.setDurationMs(durationMs);
            body.setResponse(response);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            AiHealthResponse body = new AiHealthResponse();
            body.setSuccess(false);
            body.setMessage("AI health-check failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    /**
     * Verifies mailbox fetch behavior only and returns lightweight message metadata (subject/from/date).
     * Use this endpoint to debug IMAP access independently from AI analysis and scheduler processing.
     */
    @GetMapping(path = "/api/core-test/fetch-subjects", produces = "application/json")
    @ResponseBody
    public ResponseEntity<FetchEmailSubjectsResponse> fetchEmailSubjects(
            @RequestParam(value = "maxEmails", required = false) Integer maxEmails,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        try {
            int effectiveMaxEmails = maxEmails != null && maxEmails > 0
                    ? maxEmails
                    : appSettingsService.getSchedulerMaxEmailsOrDefault();
            LocalDateTime effectiveEndDate = endDate != null ? endDate : LocalDateTime.now();
            LocalDateTime effectiveStartDate = startDate != null
                    ? startDate
                    : effectiveEndDate.minusDays(appSettingsService.getSchedulerDateRangeDaysOrDefault());

            if (effectiveStartDate.isAfter(effectiveEndDate)) {
                FetchEmailSubjectsResponse response = new FetchEmailSubjectsResponse();
                response.setSuccess(false);
                response.setMessage("startDate must be before endDate.");
                return ResponseEntity.badRequest().body(response);
            }

            Date rangeStart = Date.from(effectiveStartDate.atZone(ZoneId.systemDefault()).toInstant());
            Date rangeEnd = Date.from(effectiveEndDate.atZone(ZoneId.systemDefault()).toInstant());
            List<FetchedEmailDto> fetched = emailService.fetchEmailsByRange(effectiveMaxEmails, rangeStart, rangeEnd);

            List<EmailSubjectDto> subjects = new ArrayList<>();
            for (FetchedEmailDto email : fetched) {
                subjects.add(new EmailSubjectDto(
                        displaySubject(email.getSubject()),
                        email.getReceivedAt() != null ? email.getReceivedAt().toString() : "",
                        email.getSender() != null ? email.getSender() : ""
                ));
            }

            FetchEmailSubjectsResponse response = new FetchEmailSubjectsResponse();
            response.setSuccess(true);
            response.setMessage("Email fetch test completed.");
            response.setMailboxHost(appSettingsService.getRequiredMailSettings().getMailHost());
            response.setRequestedMaxEmails(effectiveMaxEmails);
            response.setFetchedCount(subjects.size());
            response.setStartDate(effectiveStartDate.toString());
            response.setEndDate(effectiveEndDate.toString());
            response.setSubjects(subjects);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            FetchEmailSubjectsResponse response = new FetchEmailSubjectsResponse();
            response.setSuccess(false);
            response.setMessage("Email fetch test failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/scheduler/start")
    @ResponseBody
    public ResponseEntity<SchedulerControlResponse> startScheduler(@ModelAttribute SettingsForm form) {
        if (form.getSchedulerCron() != null && !form.getSchedulerCron().isBlank()) {
            appSettingsService.updateSchedulerCron(form.getSchedulerCron());
            emailScheduler.applyCron(form.getSchedulerCron());
        }
        emailScheduler.startWithCurrentSettings();
        SchedulerControlResponse response = new SchedulerControlResponse();
        response.setSuccess(true);
        response.setRunning(emailScheduler.isRunning());
        response.setMessage("Email processing started.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/scheduler/stop")
    @ResponseBody
    public ResponseEntity<SchedulerControlResponse> stopScheduler() {
        emailScheduler.stopProcessing();
        SchedulerControlResponse response = new SchedulerControlResponse();
        response.setSuccess(true);
        response.setRunning(emailScheduler.isRunning());
        response.setMessage("Email processing stopped.");
        return ResponseEntity.ok(response);
    }

    private void applySettings(SettingsForm settingsForm) {
        appSettingsService.save(settingsForm);
        emailScheduler.applyCron(settingsForm.getSchedulerCron());
        if (Boolean.TRUE.equals(settingsForm.getSchedulerEnabled())) {
            emailScheduler.startWithCurrentSettings();
        } else {
            emailScheduler.stopProcessing();
        }
    }

    private List<ApiValidationError> validationErrors(BindingResult bindingResult) {
        Map<String, String> errors = new LinkedHashMap<>();
        bindingResult.getFieldErrors()
                .forEach(fieldError -> errors.put(fieldError.getField(), fieldError.getDefaultMessage()));

        List<ApiValidationError> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : errors.entrySet()) {
            result.add(new ApiValidationError(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private static String displaySubject(String subject) {
        return subject == null || subject.isBlank() ? "(no subject)" : subject;
    }

    private ConnectionTestResponse toConnectionTestResponse(AppSettingsService.TestEndpointResult result) {
        ConnectionTestResponse response = new ConnectionTestResponse();
        response.setSuccess(result.isSuccess());
        response.setMessage(result.getMessage());
        Map<String, Object> details = result.getDetails();
        response.setSmtpHost(asString(details.get("smtpHost")));
        response.setSmtpPort(asInteger(details.get("smtpPort")));
        response.setStatusCode(asInteger(details.get("statusCode")));
        response.setModel(asString(details.get("model")));
        return response;
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private String loadDefaultSystemPrompt() {
        ClassPathResource resource = new ClassPathResource(DEFAULT_SYSTEM_PROMPT_PATH);
        try (var inputStream = resource.getInputStream()) {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (content.isBlank()) {
                throw new IllegalStateException("Default system prompt file is empty: " + DEFAULT_SYSTEM_PROMPT_PATH);
            }
            return content;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read default system prompt file: " + DEFAULT_SYSTEM_PROMPT_PATH, e);
        }
    }
}
