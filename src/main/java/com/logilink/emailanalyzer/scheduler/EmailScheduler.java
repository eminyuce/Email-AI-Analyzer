package com.logilink.emailanalyzer.scheduler;

import com.logilink.emailanalyzer.service.AnalysisService;
import com.logilink.emailanalyzer.service.AppSettingsService;
import com.logilink.emailanalyzer.service.JobProgressService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Starts the settings-based email batch on demand (settings UI or REST). No cron and no
 * {@link org.springframework.scheduling.TaskScheduler} — only a single worker thread for fire-and-forget runs.
 */
@Component
public class EmailScheduler {

    private static final Logger log = LoggerFactory.getLogger(EmailScheduler.class);
    private static final String MANUAL_TRIGGER = "MANUAL";

    private final AnalysisService analysisService;
    private final AppSettingsService appSettingsService;
    private final JobProgressService jobProgressService;
    private final ExecutorService analysisExecutor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

    public EmailScheduler(
            AnalysisService analysisService,
            AppSettingsService appSettingsService,
            JobProgressService jobProgressService) {
        this.analysisService = analysisService;
        this.appSettingsService = appSettingsService;
        this.jobProgressService = jobProgressService;
    }

    @PreDestroy
    public void shutdown() {
        analysisExecutor.shutdown();
        try {
            if (!analysisExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                analysisExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            analysisExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Whether a batch analysis job is currently executing.
     */
    public boolean isAnalysisRunning() {
        return jobProgressService.isJobRunning();
    }

    public void runNow() {
        log.info("Manual execution of email analysis requested.");
        analysisExecutor.execute(this::runBatchJob);
    }

    public void stopCurrentAnalysis() {
        log.info("Manual stop of current email analysis requested.");
        jobProgressService.requestStop();
    }

    private void runBatchJob() {
        if (jobProgressService.snapshot(0).status() == JobProgressService.JobStatus.RUNNING) {
            log.warn("An analysis job is already running. Skipping this trigger.");
            jobProgressService.logSchedulerEvent("WARN", "Trigger skipped because an analysis is already in progress.");
            return;
        }

        log.info("Starting email analysis job...");
        try {
            int maxEmails = appSettingsService.getRequiredSchedulerMaxEmails();
            int dateRangeDays = appSettingsService.getRequiredSchedulerDateRangeDays();
            log.info("Batch job context: maxEmails={}, dateRangeDays={}", maxEmails, dateRangeDays);
            jobProgressService.startRun(MANUAL_TRIGGER, maxEmails, dateRangeDays);

            Date endDate = Date.from(Instant.now());
            Date startDate = Date.from(Instant.now().minus(dateRangeDays, ChronoUnit.DAYS));
            log.debug("Batch run date window: startDate={}, endDate={}", startDate, endDate);
            int processedCount = analysisService.processEmails(maxEmails, startDate, endDate).size();

            if (jobProgressService.isStopRequested()) {
                jobProgressService.completeRun("Analysis stopped by user. Processed emails: " + processedCount + ".");
            } else {
                jobProgressService.completeRun("Analysis finished. Processed emails: " + processedCount + ".");
            }
        } catch (Exception e) {
            log.error("Error in email analysis batch run: {}", e.getMessage(), e);
            jobProgressService.logSchedulerEvent("ERROR", "Email processing failed: " + e.getMessage());
            jobProgressService.failRun("Job failed: " + e.getMessage());
        }
    }
}
