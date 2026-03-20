package com.logilink.emailanalyzer.controller;

import com.logilink.emailanalyzer.model.SettingsForm;
import com.logilink.emailanalyzer.scheduler.EmailScheduler;
import com.logilink.emailanalyzer.service.AppSettingsService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/settings")
public class SettingsController {

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

        appSettingsService.save(settingsForm);
        emailScheduler.applyCron(settingsForm.getSchedulerCron());
        if (Boolean.TRUE.equals(settingsForm.getSchedulerEnabled())) {
            emailScheduler.startWithCurrentSettings();
        } else {
            emailScheduler.stopProcessing();
        }
        redirectAttributes.addFlashAttribute("saved", true);
        return "redirect:/settings";
    }

    @PostMapping("/test-db")
    @ResponseBody
    public Map<String, Object> testDb(@ModelAttribute SettingsForm form) {
        boolean success = appSettingsService.testDatabaseConnection(
                form.getDbHost(),
                form.getDbPort(),
                form.getDbName(),
                form.getDbUsername(),
                form.getDbPassword()
        );
        return Map.of("success", success, "message", success ? "Connection successful!" : "Connection failed. Please check your settings.");
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
}
