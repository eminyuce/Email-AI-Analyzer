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
        String cron = appSettingsService.getSchedulerCronOrDefault();
        boolean enabled = Boolean.TRUE.equals(appSettingsService.getOrCreate().getSchedulerEnabled());
        if (enabled) {
            startInternal(cron);
        } else {
            this.currentCron = cron;
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
        return currentCron != null ? currentCron : appSettingsService.getSchedulerCronOrDefault();
    }

    public synchronized void startWithCurrentSettings() {
        String cron = appSettingsService.getSchedulerCronOrDefault();
        startInternal(cron);
        appSettingsService.updateSchedulerEnabled(true);
    }

    public synchronized void stopProcessing() {
        cancelCurrentTask();
        running = false;
        appSettingsService.updateSchedulerEnabled(false);
        log.info("Email processing scheduler stopped.");
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
        this.running = true;
        log.info("Email processing scheduler started with cron: {}", cron);
    }

    private void runScheduledJob() {
        log.info("Starting scheduled email analysis job...");
        try {
            int maxEmails = appSettingsService.getSchedulerMaxEmailsOrDefault();
            int dateRangeDays = appSettingsService.getSchedulerDateRangeDaysOrDefault();
            String cron = getCurrentCron();
            jobProgressService.startRun(cron, maxEmails, dateRangeDays);

            Date endDate = Date.from(Instant.now());
            Date startDate = Date.from(Instant.now().minus(dateRangeDays, ChronoUnit.DAYS));
            int processedCount = analysisService.processEmails(maxEmails, startDate, endDate).size();
            jobProgressService.completeRun("Scheduled job finished. Processed emails: " + processedCount + ".");
        } catch (Exception e) {
            log.error("Error in scheduled email analysis: {}", e.getMessage());
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
