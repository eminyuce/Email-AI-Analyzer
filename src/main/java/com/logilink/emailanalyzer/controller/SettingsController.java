package com.logilink.emailanalyzer.controller;

import com.logilink.emailanalyzer.domain.AppSettings;
import com.logilink.emailanalyzer.domain.EmailAnalysis;
import com.logilink.emailanalyzer.model.ApiValidationError;
import com.logilink.emailanalyzer.model.DefaultSettingsTestResponse;
import com.logilink.emailanalyzer.model.EmailAnalysisReportDto;
import com.logilink.emailanalyzer.model.SettingsSaveResponse;
import com.logilink.emailanalyzer.model.SettingsForm;
import com.logilink.emailanalyzer.scheduler.EmailScheduler;
import com.logilink.emailanalyzer.service.AnalysisService;
import com.logilink.emailanalyzer.service.AppSettingsService;
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

    public SettingsController(
            AppSettingsService appSettingsService,
            EmailScheduler emailScheduler,
            AnalysisService analysisService
    ) {
        this.appSettingsService = appSettingsService;
        this.emailScheduler = emailScheduler;
        this.analysisService = analysisService;
    }

    @GetMapping
    public String settings(Model model) {
        if (!model.containsAttribute("settingsForm")) {
            SettingsForm form = SettingsForm.from(appSettingsService.getOrCreate());
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

    @PostMapping(path = "/api/save", consumes = "application/json", produces = "application/json")
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
    @GetMapping(path = "/api/save-default", produces = "application/json")
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
                reports.add(EmailAnalysisReportDto.from(analyzedEmail));
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
    public Map<String, Object> testSmtp(@ModelAttribute SettingsForm form) {
        boolean success = appSettingsService.testSmtpConnection(
                form.getMailHost(),
                form.getMailPort(),
                form.getMailUsername(),
                form.getMailPassword(),
                form.getMailSslEnabled()
        );
        return Map.of(
                "success", success,
                "message", success
                        ? "SMTP connection successful."
                        : "SMTP connection failed. Check mail host/port/username/password."
        );
    }

    @PostMapping("/test-ai")
    @ResponseBody
    public Map<String, Object> testAi(@ModelAttribute SettingsForm form) {
        boolean success = appSettingsService.testAiChatConnection(
                form.getLlmUrl(),
                form.getLlmModel(),
                form.getLlmTemperature()
        );
        return Map.of(
                "success", success,
                "message", success
                        ? "AI chat test successful."
                        : "AI chat test failed. Check LLM URL/model and ensure Ollama is running."
        );
    }

    @PostMapping("/scheduler/start")
    @ResponseBody
    public Map<String, Object> startScheduler(@ModelAttribute SettingsForm form) {
        if (form.getSchedulerCron() != null && !form.getSchedulerCron().isBlank()) {
            appSettingsService.updateSchedulerCron(form.getSchedulerCron());
            emailScheduler.applyCron(form.getSchedulerCron());
        }
        emailScheduler.startWithCurrentSettings();
        return Map.of(
                "success", true,
                "running", emailScheduler.isRunning(),
                "message", "Email processing started."
        );
    }

    @PostMapping("/scheduler/stop")
    @ResponseBody
    public Map<String, Object> stopScheduler() {
        emailScheduler.stopProcessing();
        return Map.of(
                "success", true,
                "running", emailScheduler.isRunning(),
                "message", "Email processing stopped."
        );
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
