package com.logilink.emailanalyzer.controller;

import com.logilink.emailanalyzer.mapper.AppSettingsMapper;
import com.logilink.emailanalyzer.model.ConnectionTestResponse;
import com.logilink.emailanalyzer.model.SchedulerControlResponse;
import com.logilink.emailanalyzer.model.SettingsForm;
import com.logilink.emailanalyzer.scheduler.EmailScheduler;
import com.logilink.emailanalyzer.service.AppSettingsService;
import com.logilink.emailanalyzer.service.JobProgressService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final JobProgressService jobProgressService;

    public SettingsController(
            AppSettingsService appSettingsService,
            EmailScheduler emailScheduler,
            AppSettingsMapper appSettingsMapper,
            JobProgressService jobProgressService
    ) {
        this.appSettingsService = appSettingsService;
        this.emailScheduler = emailScheduler;
        this.appSettingsMapper = appSettingsMapper;
        this.jobProgressService = jobProgressService;
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
    public String openCreateNewProfilePage() {
        return "redirect:/settings/new";
    }

    @GetMapping("/new")
    public String newSettings(Model model) {
        if (!model.containsAttribute("settingsForm")) {
            model.addAttribute("settingsForm", new SettingsForm());
        }
        model.addAttribute("profileId", null);
        model.addAttribute("activeProfileId", appSettingsService.getOrCreate().getId());
        model.addAttribute("analysisRunning", jobProgressService.isJobRunning());
        model.addAttribute("formAction", "/settings/new");
        return "settings/form";
    }

    @PostMapping("/new/save")
    public String saveNewSettings(
            @Valid @ModelAttribute("settingsForm") SettingsForm settingsForm,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.settingsForm", bindingResult);
            redirectAttributes.addFlashAttribute("settingsForm", settingsForm);
            return "redirect:/settings/new";
        }

        Long createdId = appSettingsService.createNewProfile(settingsForm).getId();
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
        model.addAttribute("analysisRunning", jobProgressService.isJobRunning());
        model.addAttribute("formAction", "/settings/" + id);
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
                form.getLlmProvider(),
                form.getLlmUrl(),
                form.getLlmModel(),
                form.getLlmTemperature()
        );
        HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(toConnectionTestResponse(result));
    }

    @PostMapping("/scheduler/run-now")
    @ResponseBody
    public ResponseEntity<SchedulerControlResponse> runNow() {
        try {
            emailScheduler.runNow();
            SchedulerControlResponse response = new SchedulerControlResponse();
            response.setSuccess(true);
            response.setRunning(jobProgressService.isJobRunning());
            response.setMessage("Email analysis batch started.");
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            SchedulerControlResponse response = new SchedulerControlResponse();
            response.setSuccess(false);
            response.setRunning(jobProgressService.isJobRunning());
            response.setMessage("Could not start analysis: " + ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @PostMapping("/scheduler/stop-analysis")
    @ResponseBody
    public ResponseEntity<SchedulerControlResponse> stopAnalysis() {
        emailScheduler.stopCurrentAnalysis();
        SchedulerControlResponse response = new SchedulerControlResponse();
        response.setSuccess(true);
        response.setRunning(jobProgressService.isJobRunning());
        response.setMessage("Stop request sent to analysis process.");
        return ResponseEntity.ok(response);
    }

    private void applySettings(Long profileId, SettingsForm settingsForm) {
        appSettingsService.saveToProfile(profileId, settingsForm);
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
