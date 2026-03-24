package com.logilink.emailanalyzer.service;

import com.logilink.emailanalyzer.common.AppConstants;
import com.logilink.emailanalyzer.domain.EmailAnalysis;
import com.logilink.emailanalyzer.model.EmailAnalysisResult;
import com.logilink.emailanalyzer.model.FetchedEmailDto;
import com.logilink.emailanalyzer.repository.EmailAnalysisRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final EmailService emailService;
    private final AIService aiService;
    private final EmailAnalysisRepository repository;
    private final MeterRegistry meterRegistry;
    private final JobProgressService jobProgressService;
    private final AppSettingsService appSettingsService;

    public AnalysisService(
            EmailService emailService,
            AIService aiService,
            EmailAnalysisRepository repository,
            MeterRegistry meterRegistry,
            JobProgressService jobProgressService,
            AppSettingsService appSettingsService
    ) {
        this.emailService = emailService;
        this.aiService = aiService;
        this.repository = repository;
        this.meterRegistry = meterRegistry;
        this.jobProgressService = jobProgressService;
        this.appSettingsService = appSettingsService;
    }

    public List<EmailAnalysis> processEmails() {
        return processEmails(Integer.MAX_VALUE);
    }

    public List<EmailAnalysis> processEmails(int maxEmails) {
        return recordLatency("processEmails", () -> {
            if (maxEmails <= 0) {
                return List.of();
            }
            List<FetchedEmailDto> emails = emailService.fetchEmails(maxEmails);
            return processMessages(emails);
        });
    }

    public List<EmailAnalysis> processEmails(int maxEmails, Date startDate, Date endDate) {
        return recordLatency("processEmailsByRange", () -> {
            if (maxEmails <= 0) {
                return List.of();
            }
            List<FetchedEmailDto> emails = emailService.fetchEmailsByRange(maxEmails, startDate, endDate);
            return processMessages(emails);
        });
    }

    /**
     * Wraps high-traffic email processing paths with latency timing and outcome tags.
     */
    private List<EmailAnalysis> recordLatency(String operation, Supplier<List<EmailAnalysis>> work) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            return work.get();
        } catch (RuntimeException ex) {
            outcome = "error";
            throw ex;
        } finally {
            sample.stop(
                    Timer.builder(AppConstants.Metrics.PROCESSING_LATENCY_METRIC)
                            .description("End-to-end email processing latency")
                            .tag("operation", operation)
                            .tag("outcome", outcome)
                            .register(meterRegistry)
            );
        }
    }

    private List<EmailAnalysis> processMessages(List<FetchedEmailDto> emails) {
        log.info("Found {} emails to analyze.", emails.size());
        jobProgressService.setTotalCandidates(emails.size());
        List<EmailAnalysis> processedAnalyses = new ArrayList<>();

        for (FetchedEmailDto email : emails) {
            if (jobProgressService.isStopRequested()) {
                log.info("Stop requested. Halting analysis process.");
                jobProgressService.logSchedulerEvent("INFO", "Process stopped by manual user request.");
                break;
            }

            try {
                String emailId = email.getEmailId();

                // Idempotency check
                if (repository.existsById(emailId)) {
                    log.info("Email {} already processed. Skipping.", emailId);
                    jobProgressService.incrementSkipped("Email " + emailId + " already processed. Skipped.");
                    continue;
                }

                String subject = email.getSubject();
                String sender = email.getSender() != null ? email.getSender() : "";
                String content = email.getContent() != null ? email.getContent() : "";

                LocalDateTime emailDate = email.getEmailDate();

                log.info("Analyzing email from: {}", sender);
                log.debug("Preparing AI analysis for emailId={}, subject='{}', sender='{}', contentLength={}",
                        emailId, subject, sender, content.length());

                EmailAnalysisResult result = aiService.analyzeEmail(emailId, subject, sender, content);
                enrichResult(result, emailId, subject, sender, emailDate);

                // Save to DB
                EmailAnalysis savedAnalysis = saveAnalysis(result);
                processedAnalyses.add(savedAnalysis);
                jobProgressService.incrementProcessed(
                        "Processed email " + emailId + " with score " + result.getCriticalityScore() + "."
                );

                log.info("Analysis Complete for email {}: Score {}", emailId, result.getCriticalityScore());

            } catch (Exception e) {
                String emailId = email.getEmailId();
                String subject = email.getSubject();
                String sender = email.getSender() != null ? email.getSender() : "";
                String content = email.getContent() != null ? email.getContent() : "";
                log.error(
                        "Analysis error for emailId={}, subject='{}', sender='{}', contentLength={}: {}",
                        emailId,
                        subject,
                        sender,
                        content.length(),
                        e.getMessage(),
                        e
                );
                jobProgressService.incrementError(
                        "Analysis error for emailId=" + emailId + ", subject='" + subject + "': " + e.getMessage()
                );
            }
        }
        return processedAnalyses;
    }

    private void enrichResult(
            EmailAnalysisResult result,
            String emailId,
            String subject,
            String sender,
            LocalDateTime emailDate
    ) {
        if (result.getEmailId() == null || result.getEmailId().isBlank()) {
            result.setEmailId(emailId);
        }
        if (result.getSubject() == null || result.getSubject().isBlank()) {
            result.setSubject(subject);
        }
        if (result.getSender() == null || result.getSender().isBlank()) {
            result.setSender(sender);
        }
        result.resolveEmailDate(emailDate);
    }

    @Transactional
    protected EmailAnalysis saveAnalysis(EmailAnalysisResult result) {
        Long settingId = appSettingsService.getOrCreate().getId();
        EmailAnalysis entity = EmailAnalysis.builder()
                .emailId(result.getEmailId())
                .settingId(settingId)
                .emailDate(result.getEmailDate())
                .subject(result.getSubject())
                .sender(result.getSender())
                .criticalityScore(result.getCriticalityScore())
                .criticalityLevel(result.getCriticalityLevel())
                .breakdown(result.getBreakdown())
                .summary(result.getSummary())
                .keyRisks(result.getKeyRisks())
                .affectedStakeholders(result.getAffectedStakeholders())
                .actionNeeded(result.getActionNeeded())
                .recommendedAction(result.getRecommendedAction())
                .estimatedResponseTime(result.getEstimatedResponseTime())
                .confidence(result.getConfidence())
                .build();

        return repository.save(entity);
    }
}
