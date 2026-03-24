package com.logilink.emailanalyzer.controller;

import com.logilink.emailanalyzer.service.JobProgressService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping
@PreAuthorize("hasRole('ADMIN')")
public class JobProgressController {

    private final JobProgressService jobProgressService;

    public JobProgressController(JobProgressService jobProgressService) {
        this.jobProgressService = jobProgressService;
    }

    @GetMapping("/jobs/progress")
    public String progressPage() {
        return "jobs/progress";
    }

    @GetMapping("/api/jobs/progress")
    @ResponseBody
    public JobProgressService.ProgressSnapshot progressSnapshot(
            @RequestParam(name = "sinceId", defaultValue = "0") long sinceId) {
        return jobProgressService.snapshot(Math.max(0, sinceId));
    }
}
