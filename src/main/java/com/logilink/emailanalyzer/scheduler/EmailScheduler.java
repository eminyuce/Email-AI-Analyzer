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
        boolean enabled = Boolean.TRUE.equals(appSettingsService.getOrCreate().getSchedulerEnabled());
        if (!enabled) {
            running = false;
            jobProgressService.logSchedulerEvent("INFO", "Scheduler is disabled in active settings; cron job not started.");
            return;
        }
        try {
            String cron = appSettingsService.getRequiredSchedulerCron();
            startInternal(cron);
        } catch (Exception ex) {
            running = false;
            String message = "Scheduler is enabled but failed to start: " + ex.getMessage();
            log.error(message, ex);
            jobProgressService.logSchedulerEvent("ERROR", message);
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

    public synchronized void stopProcessing() {
        cancelCurrentTask();
        running = false;
        appSettingsService.updateSchedulerEnabled(false);
        log.info("Email processing scheduler stopped.");
        jobProgressService.logSchedulerEvent("INFO", "Scheduler stopped.");
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
        log.info("Starting scheduled email analysis job...");
        jobProgressService.logSchedulerEvent("INFO", "Cron trigger fired. Starting scheduled email processing.");
        try {
            int maxEmails = appSettingsService.getRequiredSchedulerMaxEmails();
            int dateRangeDays = appSettingsService.getRequiredSchedulerDateRangeDays();
            String cron = getCurrentCron();
            jobProgressService.startRun(cron, maxEmails, dateRangeDays);

            Date endDate = Date.from(Instant.now());
            Date startDate = Date.from(Instant.now().minus(dateRangeDays, ChronoUnit.DAYS));
            int processedCount = analysisService.processEmails(maxEmails, startDate, endDate).size();
            jobProgressService.completeRun("Scheduled job finished. Processed emails: " + processedCount + ".");
        } catch (Exception e) {
            log.error("Error in scheduled email analysis: {}", e.getMessage());
            jobProgressService.logSchedulerEvent("ERROR", "Scheduled email processing failed: " + e.getMessage());
            jobProgressService.failRun("Scheduled job failed: " + e.getMessage());
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
