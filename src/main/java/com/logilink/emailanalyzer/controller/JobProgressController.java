package com.logilink.emailanalyzer.controller;

import com.logilink.emailanalyzer.domain.AppSettings;
import com.logilink.emailanalyzer.service.AppSettingsService;
import com.logilink.emailanalyzer.service.JobProgressService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

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
                active.getLlmProvider(),
                active.getLlmModel(),
                active.getLlmUrl(),
                active.getSchedulerDateRangeDays(),
                active.getSchedulerMaxEmails()
        );
        return new ProgressPageSnapshot(progress, settings);
    }

    @PostMapping("/api/jobs/progress/clear-logs")
    @ResponseBody
    public Map<String, Object> clearProgressLogs() {
        jobProgressService.clearLogs();
        return Map.of("success", true);
    }

    @GetMapping("/api/jobs/progress/logs-export")
    public ResponseEntity<byte[]> exportProgressLogs() {
        String body = jobProgressService.exportLogsAsTextNewestFirst();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"email-job-live-logs.txt\"")
                .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                .body(bytes);
    }

    public record ActiveSettingsSummary(
            Long id,
            String mailHost,
            Integer mailPort,
            String mailUsername,
            String llmProvider,
            String llmModel,
            String llmUrl,
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
