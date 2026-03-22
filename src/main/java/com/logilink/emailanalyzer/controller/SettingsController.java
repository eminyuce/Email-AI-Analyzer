package com.logilink.emailanalyzer.controller;

import com.logilink.emailanalyzer.domain.AppSettings;
import com.logilink.emailanalyzer.model.SettingsForm;
import com.logilink.emailanalyzer.scheduler.EmailScheduler;
import com.logilink.emailanalyzer.service.AppSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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

    public SettingsController(AppSettingsService appSettingsService, EmailScheduler emailScheduler) {
        this.appSettingsService = appSettingsService;
        this.emailScheduler = emailScheduler;
    }

    @GetMapping
    public String settings(Model model) {
        if (!model.containsAttribute("settingsForm")) {
            model.addAttribute("settingsForm", SettingsForm.from(appSettingsService.getOrCreate()));
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
    public ResponseEntity<Map<String, Object>> saveSettingsApi(
            @Valid @RequestBody SettingsForm settingsForm,
            BindingResult bindingResult
    ) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Validation failed for SettingsForm.",
                    "errors", validationErrors(bindingResult)
            ));
        }

        applySettings(settingsForm);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Settings saved successfully.",
                "schedulerRunning", emailScheduler.isRunning()
        ));
    }

    @GetMapping(path = "/api/save-default", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveDefaultSettingsApi(
            @RequestParam("modelName") String modelName,
            @RequestParam("mailPassword") String mailPassword
    ) {
        if (modelName == null || modelName.isBlank() || mailPassword == null || mailPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Both modelName and mailPassword are required."
            ));
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

        applySettings(settingsForm);
        return ResponseEntity.status(HttpStatus.OK).body(Map.of(
                "success", true,
                "message", "Default settings saved successfully.",
                "schedulerRunning", emailScheduler.isRunning(),
                "mailUsername", DEFAULT_MAIL_USERNAME,
                "llmModel", modelName,
                "schedulerCronChanged", false
        ));
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

    private Map<String, String> validationErrors(BindingResult bindingResult) {
        Map<String, String> errors = new LinkedHashMap<>();
        bindingResult.getFieldErrors()
                .forEach(fieldError -> errors.put(fieldError.getField(), fieldError.getDefaultMessage()));
        return errors;
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
