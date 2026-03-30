package com.logilink.emailanalyzer.service;

import com.logilink.emailanalyzer.common.AppConstants;
import com.logilink.emailanalyzer.domain.EmailAnalysis;
import com.logilink.emailanalyzer.model.EmailAnalysisResult;
import com.logilink.emailanalyzer.model.FetchedEmailDto;
import com.logilink.emailanalyzer.repository.EmailAnalysisRepository;
import com.logilink.emailanalyzer.util.EmailContentNormalizer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Set;
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
            List<FetchedEmailDto> emails = emailService.fetchEmails(
                    maxEmails,
                    ids -> ids.isEmpty() ? Set.of() : repository.findExistingEmailIdsIn(ids));
            return processMessages(emails);
        });
    }

    public List<EmailAnalysis> processEmails(int maxEmails, Date startDate, Date endDate) {
        return recordLatency("processEmailsByRange", () -> {
            if (maxEmails <= 0) {
                return List.of();
            }
            List<FetchedEmailDto> emails = emailService.fetchEmailsByRange(
                    maxEmails,
                    startDate,
                    endDate,
                    ids -> ids.isEmpty() ? Set.of() : repository.findExistingEmailIdsIn(ids));
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
        if (emails.isEmpty()) {
            log.info("No emails to analyze.");
            return List.of();
        }

        log.info("Starting analysis of {} emails using virtual threads.", emails.size());
        jobProgressService.setTotalCandidates(emails.size());

        int maxConcurrency = 10; // Limit concurrent AI requests to avoid rate limits
        java.util.concurrent.Semaphore semaphore = new java.util.concurrent.Semaphore(maxConcurrency);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

        List<java.util.concurrent.CompletableFuture<EmailAnalysis>> futures = emails.stream()
                .map(email -> java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    if (jobProgressService.isStopRequested()) {
                        return null;
                    }

                    try {
                        semaphore.acquire();
                        try {
                            return analyzeSingleEmail(email);
                        } finally {
                            semaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }, executor))
                .toList();

        List<EmailAnalysis> processedAnalyses = futures.stream()
                .map(java.util.concurrent.CompletableFuture::join)
                .filter(java.util.Objects::nonNull)
                .toList();

        log.info("Finished analysis of {} emails. Successfully processed: {}", emails.size(), processedAnalyses.size());
        return processedAnalyses;
    }

    private EmailAnalysis analyzeSingleEmail(FetchedEmailDto email) {
        String emailId = email.getEmailId();
        try {
            // Idempotency check
            if (repository.existsByEmailId(emailId)) {
                log.info("Email {} already processed. Skipping.", emailId);
                jobProgressService.incrementSkipped("Email " + emailId + " already processed. Skipped.");
                return null;
            }

            String subject = email.getSubject();
            String sender = email.getSenderLine();
            String content = email.getContent() != null ? email.getContent() : "";
            LocalDateTime emailDate = email.getEmailDate();

            log.info("Analyzing email: '{}' from {}", subject, sender);
            long start = System.currentTimeMillis();

            EmailAnalysisResult result = aiService.analyzeEmail(
                    emailId,
                    subject,
                    sender,
                    content,
                    email.getInReplyTo(),
                    email.getReferences(),
                    email.getAttachments()
            );
            if(result.isNotProcessedByLLM()){
                log.info("Email {} is not processed by LLM. Skipping.", emailId);
                jobProgressService.incrementSkipped("Email " + emailId + " is not processed by LLM. Skipped.");
                return null;
            }
            enrichResult(result, emailId, subject, sender, emailDate);

            // Save to DB
            EmailAnalysis savedAnalysis = saveAnalysis(
                    result,
                    content,
                    email.getInReplyTo(),
                    email.getReferences()
            );
            jobProgressService.incrementProcessed(
                    "Processed email " + emailId + " with score " + result.getCriticalityScore() + "."
            );

            long end = System.currentTimeMillis();
            log.info("Analysis complete for email '{}' (Score: {}, Time: {} ms)", subject, result.getCriticalityScore(), (end - start));
            return savedAnalysis;

        } catch (Exception e) {
            log.error("Analysis error for emailId {}: {}", emailId, e.getMessage());
            jobProgressService.incrementError("Analysis error for emailId=" + emailId + ": " + e.getMessage());
            return null;
        }
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
    protected EmailAnalysis saveAnalysis(
            EmailAnalysisResult result,
            String content,
            String inReplyTo,
            String references
    ) {
        Long settingId = appSettingsService.getOrCreate().getId();
        String storedContent = EmailContentNormalizer.normalize(content);
        EmailAnalysis entity = EmailAnalysis.builder()
                .emailId(result.getEmailId())
                .settingId(settingId)
                .emailDate(result.getEmailDate())
                .subject(result.getSubject())
                .sender(result.getSender())
                .content(storedContent)
                .inReplyTo(inReplyTo)
                .emailReferences(references)
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
