package com.logilink.emailanalyzer.controller;

import com.logilink.emailanalyzer.mapper.AppSettingsMapper;
import com.logilink.emailanalyzer.model.ConnectionTestResponse;
import com.logilink.emailanalyzer.model.SchedulerControlResponse;
import com.logilink.emailanalyzer.model.SettingsForm;
import com.logilink.emailanalyzer.scheduler.EmailScheduler;
import com.logilink.emailanalyzer.service.AppSettingsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/settings")
@PreAuthorize("hasRole('ADMIN')")
public class SettingsController {

    private final AppSettingsService appSettingsService;
    private final EmailScheduler emailScheduler;
    private final AppSettingsMapper appSettingsMapper;

    public SettingsController(
            AppSettingsService appSettingsService,
            EmailScheduler emailScheduler,
            AppSettingsMapper appSettingsMapper
    ) {
        this.appSettingsService = appSettingsService;
        this.emailScheduler = emailScheduler;
        this.appSettingsMapper = appSettingsMapper;
    }

    @GetMapping
    public String settings(Model model) {
        Long activeProfileId = appSettingsService.getOrCreate().getId();
        return "redirect:/settings/" + activeProfileId;
    }

    @GetMapping("/list")
    public String settingsList(Model model) {
        model.addAttribute("profiles", appSettingsService.listAll());
        model.addAttribute("activeProfileId", appSettingsService.getOrCreate().getId());
        return "settings/list";
    }

    @PostMapping("/new")
    public String createNewProfile(RedirectAttributes redirectAttributes) {
        Long createdId = appSettingsService.createNewProfile().getId();
        redirectAttributes.addFlashAttribute("saved", true);
        return "redirect:/settings/" + createdId;
    }

    @PostMapping("/duplicate")
    public String duplicateProfile(RedirectAttributes redirectAttributes) {
        Long createdId = appSettingsService.createProfileFromActive().getId();
        redirectAttributes.addFlashAttribute("saved", true);
        return "redirect:/settings/" + createdId;
    }

    @PostMapping("/{id}/activate")
    public String activateProfile(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        appSettingsService.activateProfile(id);
        emailScheduler.syncWithActiveSettings();
        redirectAttributes.addFlashAttribute("saved", true);
        return "redirect:/settings/list";
    }

    @GetMapping("/{id}")
    public String editSettings(@PathVariable Long id, Model model) {
        if (!model.containsAttribute("settingsForm")) {
            SettingsForm form = appSettingsMapper.toSettingsForm(appSettingsService.getById(id));
            model.addAttribute("settingsForm", form);
        }
        model.addAttribute("profileId", id);
        model.addAttribute("activeProfileId", appSettingsService.getOrCreate().getId());
        model.addAttribute("schedulerRunning", emailScheduler.isRunning());
        return "settings/form";
    }

    @PostMapping("/{id}")
    public String saveSettings(
            @PathVariable Long id,
            @Valid @ModelAttribute("settingsForm") SettingsForm settingsForm,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.settingsForm", bindingResult);
            redirectAttributes.addFlashAttribute("settingsForm", settingsForm);
            return "redirect:/settings/" + id;
        }

        applySettings(id, settingsForm);
        // Reflect persisted values after redirect so users see what is stored.
        SettingsForm persistedForm = appSettingsMapper.toSettingsForm(appSettingsService.getById(id));
        redirectAttributes.addFlashAttribute("settingsForm", persistedForm);
        redirectAttributes.addFlashAttribute("saved", true);
        return "redirect:/settings/" + id;
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

    @PostMapping("/scheduler/start")
    @ResponseBody
    public ResponseEntity<SchedulerControlResponse> startScheduler(@ModelAttribute SettingsForm form) {
        try {
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
        } catch (Exception ex) {
            SchedulerControlResponse response = new SchedulerControlResponse();
            response.setSuccess(false);
            response.setRunning(emailScheduler.isRunning());
            response.setMessage("Email processing could not be started: " + ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
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

    private void applySettings(Long profileId, SettingsForm settingsForm) {
        appSettingsService.saveToProfile(profileId, settingsForm);
        Long activeProfileId = appSettingsService.getOrCreate().getId();
        if (!activeProfileId.equals(profileId)) {
            return;
        }
        emailScheduler.applyCron(settingsForm.getSchedulerCron());
        if (Boolean.TRUE.equals(settingsForm.getSchedulerEnabled())) {
            emailScheduler.startWithCurrentSettings();
        } else {
            emailScheduler.stopProcessing();
        }
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
}
