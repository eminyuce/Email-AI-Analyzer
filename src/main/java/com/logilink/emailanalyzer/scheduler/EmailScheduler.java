package com.logilink.emailanalyzer.scheduler;

import com.logilink.emailanalyzer.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailScheduler {

    private final AnalysisService analysisService;

    @Scheduled(cron = "${email.analysis.cron}")
    public void scheduleEmailAnalysis() {
        log.info("Starting scheduled email analysis job...");
        try {
            analysisService.processEmails();
        } catch (Exception e) {
            log.error("Error in scheduled email analysis: {}", e.getMessage());
        }
    }
}
