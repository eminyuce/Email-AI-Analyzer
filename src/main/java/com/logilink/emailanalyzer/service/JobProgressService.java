package com.logilink.emailanalyzer.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class JobProgressService {

    public enum JobStatus {
        IDLE,
        RUNNING,
        COMPLETED,
        FAILED
    }

    public record ProgressLogEntry(long id, Instant timestamp, String level, String status, String message) {
    }

    public record ProgressSnapshot(
            JobStatus status,
            String schedulerCron,
            Instant startedAt,
            Instant completedAt,
            Integer maxEmailsRequested,
            Integer dateRangeDays,
            int totalCandidates,
            int processedCount,
            int skippedCount,
            int errorCount,
            long lastLogId,
            List<ProgressLogEntry> logs
    ) {
    }

    private static final int MAX_LOG_ENTRIES = 500;

    private final AtomicLong nextLogId = new AtomicLong(0);
    private final Deque<ProgressLogEntry> logs = new ConcurrentLinkedDeque<>();

    private volatile JobStatus status = JobStatus.IDLE;
    private volatile String schedulerCron = "";
    private volatile Instant startedAt;
    private volatile Instant completedAt;
    private volatile Integer maxEmailsRequested;
    private volatile Integer dateRangeDays;
    private volatile int totalCandidates;
    private volatile int processedCount;
    private volatile int skippedCount;
    private volatile int errorCount;

    public synchronized void startRun(String cron, int maxEmails, int rangeDays) {
        status = JobStatus.RUNNING;
        schedulerCron = cron;
        startedAt = Instant.now();
        completedAt = null;
        maxEmailsRequested = maxEmails;
        dateRangeDays = rangeDays;
        totalCandidates = 0;
        processedCount = 0;
        skippedCount = 0;
        errorCount = 0;
        append("INFO", "RUNNING", "Scheduled job started.");
    }

    public synchronized void setTotalCandidates(int total) {
        totalCandidates = Math.max(0, total);
        append("INFO", "RUNNING", "Found " + totalCandidates + " candidate emails.");
    }

    public synchronized void incrementProcessed(String message) {
        processedCount++;
        append("INFO", "RUNNING", message);
    }

    public synchronized void incrementSkipped(String message) {
        skippedCount++;
        append("WARN", "RUNNING", message);
    }

    public synchronized void incrementError(String message) {
        errorCount++;
        append("ERROR", "RUNNING", message);
    }

    public synchronized void completeRun(String message) {
        status = JobStatus.COMPLETED;
        completedAt = Instant.now();
        append("INFO", "COMPLETED", message);
    }

    public synchronized void failRun(String message) {
        status = JobStatus.FAILED;
        completedAt = Instant.now();
        append("ERROR", "FAILED", message);
    }

    public synchronized void logSchedulerEvent(String level, String message) {
        String normalizedLevel = level == null ? "INFO" : level.trim().toUpperCase();
        if (!"INFO".equals(normalizedLevel) && !"WARN".equals(normalizedLevel) && !"ERROR".equals(normalizedLevel)) {
            normalizedLevel = "INFO";
        }
        append(normalizedLevel, "SCHEDULER", message);
    }

    public ProgressSnapshot snapshot(long sinceId) {
        List<ProgressLogEntry> selectedLogs = new ArrayList<>();
        for (ProgressLogEntry entry : logs) {
            if (entry.id() > sinceId) {
                selectedLogs.add(entry);
            }
        }
        selectedLogs.sort(Comparator.comparingLong(ProgressLogEntry::id));
        long lastLogId = selectedLogs.isEmpty() ? sinceId : selectedLogs.get(selectedLogs.size() - 1).id();
        return new ProgressSnapshot(
                status,
                schedulerCron,
                startedAt,
                completedAt,
                maxEmailsRequested,
                dateRangeDays,
                totalCandidates,
                processedCount,
                skippedCount,
                errorCount,
                lastLogId,
                selectedLogs
        );
    }

    private void append(String level, String statusLabel, String message) {
        long id = nextLogId.incrementAndGet();
        logs.addLast(new ProgressLogEntry(id, Instant.now(), level, statusLabel, message));
        while (logs.size() > MAX_LOG_ENTRIES) {
            logs.pollFirst();
        }
    }
}
