package com.logilink.emailanalyzer.scheduler;

import com.logilink.emailanalyzer.service.AnalysisService;
import com.logilink.emailanalyzer.service.AppSettingsService;
import com.logilink.emailanalyzer.service.JobProgressService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;

@Component
public class EmailScheduler {

    private static final Logger log = LoggerFactory.getLogger(EmailScheduler.class);

    private final AnalysisService analysisService;
    private final AppSettingsService appSettingsService;
    private final JobProgressService jobProgressService;
    private final ThreadPoolTaskScheduler taskScheduler;
    private ScheduledFuture<?> scheduledTask;
    private volatile boolean running;
    private volatile String currentCron;

    public EmailScheduler(
            AnalysisService analysisService,
            AppSettingsService appSettingsService,
            JobProgressService jobProgressService) {
        this.analysisService = analysisService;
        this.appSettingsService = appSettingsService;
        this.jobProgressService = jobProgressService;
        this.taskScheduler = new ThreadPoolTaskScheduler();
        this.taskScheduler.setPoolSize(1);
        this.taskScheduler.setThreadNamePrefix("email-scheduler-");
        this.taskScheduler.initialize();
    }

    @PostConstruct
    public void init() {
        log.info("Initializing EmailScheduler...");
        try {
            syncWithActiveSettings();
        } catch (Exception e) {
            log.error("Failed to sync scheduler with active settings on startup: {}", e.getMessage());
        }
    }

    @PreDestroy
    public synchronized void shutdown() {
        cancelCurrentTask();
        taskScheduler.shutdown();
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized String getCurrentCron() {
        return currentCron;
    }

    public synchronized void startWithCurrentSettings() {
        try {
            String cron = appSettingsService.getRequiredSchedulerCron();
            // Fail fast on incomplete scheduler settings instead of failing only when cron fires.
            appSettingsService.getRequiredSchedulerDateRangeDays();
            appSettingsService.getRequiredSchedulerMaxEmails();
            startInternal(cron);
            appSettingsService.updateSchedulerEnabled(true);
        } catch (Exception ex) {
            running = false;
            String message = "Scheduler start failed: " + ex.getMessage();
            log.error(message, ex);
            jobProgressService.logSchedulerEvent("ERROR", message);
            throw ex;
        }
    }

    public synchronized void syncWithActiveSettings() {
        var settings = appSettingsService.getOrCreate();
        boolean enabled = Boolean.TRUE.equals(settings.getSchedulerEnabled());
        String cron = settings.getSchedulerCron();
        
        if (!enabled) {
            cancelCurrentTask();
            running = false;
            currentCron = cron;
            log.info("Scheduler is disabled in active settings.");
            jobProgressService.logSchedulerEvent("INFO", "Scheduler is disabled in active settings; cron job not started.");
            return;
        }

        try {
            if (cron == null || cron.isBlank()) {
                throw new IllegalStateException("Cron expression is empty");
            }
            appSettingsService.getRequiredSchedulerDateRangeDays();
            appSettingsService.getRequiredSchedulerMaxEmails();
            startInternal(cron);
        } catch (Exception ex) {
            cancelCurrentTask();
            running = false;
            String message = "Active settings scheduler could not be started: " + ex.getMessage();
            log.error(message, ex);
            jobProgressService.logSchedulerEvent("ERROR", message);
        }
    }

    public synchronized void stopProcessing() {
        cancelCurrentTask();
        running = false;
        appSettingsService.updateSchedulerEnabled(false);
        log.info("Email processing scheduler stopped.");
        jobProgressService.logSchedulerEvent("INFO", "Scheduler (cron) stopped.");
    }

    public void runNow() {
        log.info("Manual execution of email analysis requested.");
        taskScheduler.execute(this::runScheduledJob);
    }

    public void stopCurrentAnalysis() {
        log.info("Manual stop of current email analysis requested.");
        jobProgressService.requestStop();
    }

    public synchronized void applyCron(String cron) {
        this.currentCron = cron;
        if (running) {
            startInternal(cron);
        }
    }

    private void startInternal(String cron) {
        validateCron(cron);
        cancelCurrentTask();
        this.currentCron = cron;
        this.scheduledTask = taskScheduler.schedule(this::runScheduledJob, new CronTrigger(cron));
        if (this.scheduledTask == null) {
            throw new IllegalStateException("Task scheduler returned null while scheduling cron job.");
        }
        this.running = true;
        log.info("Email processing scheduler started with cron: {}", cron);
        jobProgressService.logSchedulerEvent("INFO", "Scheduler started with cron: " + cron);
    }

    private void runScheduledJob() {
        if (jobProgressService.snapshot(0).status() == JobProgressService.JobStatus.RUNNING) {
            log.warn("An analysis job is already running. Skipping this trigger.");
            jobProgressService.logSchedulerEvent("WARN", "Trigger skipped because an analysis is already in progress.");
            return;
        }

        log.info("Starting email analysis job...");
        try {
            int maxEmails = appSettingsService.getRequiredSchedulerMaxEmails();
            int dateRangeDays = appSettingsService.getRequiredSchedulerDateRangeDays();
            String cron = getCurrentCron();
            jobProgressService.startRun(cron != null ? cron : "MANUAL", maxEmails, dateRangeDays);

            Date endDate = Date.from(Instant.now());
            Date startDate = Date.from(Instant.now().minus(dateRangeDays, ChronoUnit.DAYS));
            int processedCount = analysisService.processEmails(maxEmails, startDate, endDate).size();
            
            if (jobProgressService.isStopRequested()) {
                jobProgressService.completeRun("Analysis stopped by user. Processed emails: " + processedCount + ".");
            } else {
                jobProgressService.completeRun("Analysis finished. Processed emails: " + processedCount + ".");
            }
        } catch (Exception e) {
            log.error("Error in email analysis: {}", e.getMessage());
            jobProgressService.logSchedulerEvent("ERROR", "Email processing failed: " + e.getMessage());
            jobProgressService.failRun("Job failed: " + e.getMessage());
        }
    }

    private void cancelCurrentTask() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }
    }

    private void validateCron(String cron) {
        CronExpression.parse(cron);
    }
}
