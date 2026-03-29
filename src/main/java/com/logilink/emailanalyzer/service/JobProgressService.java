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

    /**
     * One progress stream for this app: batch email analysis (manual / API triggers).
     */
    public static final String JOB_TYPE_EMAIL_ANALYSIS = "EMAIL_ANALYSIS";

    public record ProgressSnapshot(
            JobStatus status,
            String runTrigger,
            Instant startedAt,
            Instant completedAt,
            Integer maxEmailsRequested,
            Integer dateRangeDays,
            int totalCandidates,
            int processedCount,
            int skippedCount,
            int errorCount,
            long lastLogId,
            List<ProgressLogEntry> logs,
            /** True only while a batch analysis job is actively running. */
            boolean jobRunning,
            /** What this service tracks ({@link #JOB_TYPE_EMAIL_ANALYSIS}) when a run exists or is in progress. */
            String activeJobType,
            boolean stopRequested,
            /** One short sentence suitable for badges and headers. */
            String statusSentence,
            /** Why the system is in this state (counts, last outcome, latest scheduler note). */
            String statusReason,
            String lastSchedulerEventMessage
    ) {
    }

    private static final int MAX_LOG_ENTRIES = 500;

    private final AtomicLong nextLogId = new AtomicLong(0);
    private final Deque<ProgressLogEntry> logs = new ConcurrentLinkedDeque<>();

    private volatile JobStatus status = JobStatus.IDLE;
    private volatile boolean stopRequested = false;
    private volatile String runTrigger = "";
    private volatile Instant startedAt;
    private volatile Instant completedAt;
    private volatile Integer maxEmailsRequested;
    private volatile Integer dateRangeDays;
    private volatile int totalCandidates;
    private volatile int processedCount;
    private volatile int skippedCount;
    private volatile int errorCount;
    private volatile String lastTerminalRunMessage = "";
    private volatile String lastSchedulerEventMessage = "";

    public boolean isJobRunning() {
        return status == JobStatus.RUNNING;
    }

    public synchronized void startRun(String trigger, int maxEmails, int rangeDays) {
        status = JobStatus.RUNNING;
        stopRequested = false;
        lastTerminalRunMessage = "";
        runTrigger = trigger == null ? "" : trigger;
        startedAt = Instant.now();
        completedAt = null;
        maxEmailsRequested = maxEmails;
        dateRangeDays = rangeDays;
        totalCandidates = 0;
        processedCount = 0;
        skippedCount = 0;
        errorCount = 0;
        append("INFO", "RUNNING", "Analysis job started.");
    }

    public synchronized void requestStop() {
        if (status == JobStatus.RUNNING) {
            stopRequested = true;
            append("WARN", "STOPPING", "Stop requested by user. Finalizing current email and stopping...");
        }
    }

    public boolean isStopRequested() {
        return stopRequested;
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
        lastTerminalRunMessage = message == null ? "" : message;
        append("INFO", "COMPLETED", message);
    }

    public synchronized void failRun(String message) {
        status = JobStatus.FAILED;
        completedAt = Instant.now();
        lastTerminalRunMessage = message == null ? "" : message;
        append("ERROR", "FAILED", message);
    }

    public synchronized void logSchedulerEvent(String level, String message) {
        String normalizedLevel = level == null ? "INFO" : level.trim().toUpperCase();
        if (!"INFO".equals(normalizedLevel) && !"WARN".equals(normalizedLevel) && !"ERROR".equals(normalizedLevel)) {
            normalizedLevel = "INFO";
        }
        if (message != null) {
            lastSchedulerEventMessage =
                    message.length() > 500 ? message.substring(0, 500) + "…" : message;
        }
        append(normalizedLevel, "SCHEDULER", message);
    }

    /**
     * Removes all buffered live log lines and resets log id sequence. Job status and counters are unchanged.
     */
    public synchronized void clearLogs() {
        logs.clear();
        nextLogId.set(0);
    }

    /**
     * All buffered log lines as plain text, one per line, newest first (tab-separated fields).
     */
    public synchronized String exportLogsAsTextNewestFirst() {
        List<ProgressLogEntry> copy = new ArrayList<>(logs);
        copy.sort(Comparator.comparingLong(ProgressLogEntry::id).reversed());
        StringBuilder sb = new StringBuilder(Math.min(256 + copy.size() * 64, 256_000));
        sb.append("timestamp\tlevel\tstatus\tmessage\n");
        for (ProgressLogEntry e : copy) {
            String msg = e.message() == null ? "" : e.message();
            msg = msg.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
            sb.append(e.timestamp().toString()).append('\t')
                    .append(e.level()).append('\t')
                    .append(e.status()).append('\t')
                    .append(msg)
                    .append('\n');
        }
        return sb.toString();
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
        boolean jobRunning = status == JobStatus.RUNNING;
        String activeJobType = jobRunning ? JOB_TYPE_EMAIL_ANALYSIS : null;
        String sentence = buildStatusSentence();
        String reason = buildStatusReason();
        return new ProgressSnapshot(
                status,
                runTrigger,
                startedAt,
                completedAt,
                maxEmailsRequested,
                dateRangeDays,
                totalCandidates,
                processedCount,
                skippedCount,
                errorCount,
                lastLogId,
                selectedLogs,
                jobRunning,
                activeJobType,
                stopRequested,
                sentence,
                reason,
                lastSchedulerEventMessage
        );
    }

    private String buildStatusSentence() {
        return switch (status) {
            case RUNNING -> {
                String trigger = describeTrigger(runTrigger);
                if (stopRequested) {
                    yield "Stopping: email analysis is finishing the current message (" + trigger + ").";
                }
                yield "Running: email analysis in progress (" + trigger + ").";
            }
            case COMPLETED -> "Not running: last email analysis finished successfully.";
            case FAILED -> "Not running: last email analysis run failed.";
            case IDLE -> "Not running: idle (no analysis job is active).";
        };
    }

    private String buildStatusReason() {
        StringBuilder sb = new StringBuilder();
        switch (status) {
            case RUNNING -> {
                sb.append("Job type: batch email analysis. ");
                if (maxEmailsRequested != null && dateRangeDays != null) {
                    sb.append("This run may fetch up to ")
                            .append(maxEmailsRequested)
                            .append(" messages from roughly the last ")
                            .append(dateRangeDays)
                            .append(" days. ");
                }
                if (totalCandidates > 0) {
                    sb.append("So far: ")
                            .append(processedCount)
                            .append(" processed, ")
                            .append(skippedCount)
                            .append(" skipped, ")
                            .append(errorCount)
                            .append(" errors, out of ")
                            .append(totalCandidates)
                            .append(" candidates.");
                } else {
                    sb.append("Candidate emails are still being loaded.");
                }
                if (stopRequested) {
                    sb.append(" A stop was requested; the current message will finish, then the job ends.");
                }
            }
            case COMPLETED -> {
                sb.append("The worker is idle until the next manual batch run. ");
                if (lastTerminalRunMessage != null && !lastTerminalRunMessage.isBlank()) {
                    sb.append("Outcome: ").append(lastTerminalRunMessage);
                }
            }
            case FAILED -> {
                sb.append("Fix the error below (or in logs), then run the batch again. ");
                if (lastTerminalRunMessage != null && !lastTerminalRunMessage.isBlank()) {
                    sb.append("Failure detail: ").append(lastTerminalRunMessage);
                }
            }
            case IDLE -> {
                sb.append("No batch has started since the application started, or state was reset. ");
                sb.append("Use Run Now on settings or the API to start email analysis.");
            }
        }
        if (lastSchedulerEventMessage != null && !lastSchedulerEventMessage.isBlank()) {
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                sb.append(' ');
            }
            sb.append("Latest job event: ").append(lastSchedulerEventMessage);
        }
        return sb.toString().trim();
    }

    private static String describeTrigger(String trigger) {
        if (trigger == null || trigger.isBlank()) {
            return "trigger not recorded";
        }
        if ("MANUAL".equalsIgnoreCase(trigger.trim())) {
            return "manual / API";
        }
        return "'" + trigger + "'";
    }

    private void append(String level, String statusLabel, String message) {
        long id = nextLogId.incrementAndGet();
        logs.addLast(new ProgressLogEntry(id, Instant.now(), level, statusLabel, message));
        while (logs.size() > MAX_LOG_ENTRIES) {
            logs.pollFirst();
        }
    }
}
