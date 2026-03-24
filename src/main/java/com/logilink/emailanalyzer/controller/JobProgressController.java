package com.logilink.emailanalyzer.controller;

import com.logilink.emailanalyzer.domain.AppSettings;
import com.logilink.emailanalyzer.service.AppSettingsService;
import com.logilink.emailanalyzer.service.JobProgressService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping
@PreAuthorize("hasRole('ADMIN')")
public class JobProgressController {

    private final JobProgressService jobProgressService;
    private final AppSettingsService appSettingsService;

    public JobProgressController(
            JobProgressService jobProgressService,
            AppSettingsService appSettingsService) {
        this.jobProgressService = jobProgressService;
        this.appSettingsService = appSettingsService;
    }

    @GetMapping("/jobs/progress")
    public String progressPage(Model model) {
        AppSettings active = appSettingsService.getOrCreate();
        model.addAttribute("activeSettingsId", active.getId());
        return "jobs/progress";
    }

    @GetMapping("/api/jobs/progress")
    @ResponseBody
    public ProgressPageSnapshot progressSnapshot(
            @RequestParam(name = "sinceId", defaultValue = "0") long sinceId) {
        JobProgressService.ProgressSnapshot progress = jobProgressService.snapshot(Math.max(0, sinceId));
        AppSettings active = appSettingsService.getOrCreate();
        ActiveSettingsSummary settings = new ActiveSettingsSummary(
                active.getId(),
                active.getMailHost(),
                active.getMailPort(),
                active.getMailUsername(),
                active.getLlmModel(),
                active.getLlmUrl(),
                active.getSchedulerCron(),
                active.getSchedulerDateRangeDays(),
                active.getSchedulerMaxEmails()
        );
        return new ProgressPageSnapshot(progress, settings);
    }

    public record ActiveSettingsSummary(
            Long id,
            String mailHost,
            Integer mailPort,
            String mailUsername,
            String llmModel,
            String llmUrl,
            String schedulerCron,
            Integer schedulerDateRangeDays,
            Integer schedulerMaxEmails
    ) {
    }

    public record ProgressPageSnapshot(
            JobProgressService.ProgressSnapshot progress,
            ActiveSettingsSummary activeSettings
    ) {
    }
}
