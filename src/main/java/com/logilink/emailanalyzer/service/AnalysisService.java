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

import java.util.ArrayList;
import java.time.LocalDateTime;
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

  public AnalysisService(
      EmailService emailService,
      AIService aiService,
      EmailAnalysisRepository repository,
      MeterRegistry meterRegistry
  ) {
    this.emailService = emailService;
    this.aiService = aiService;
    this.repository = repository;
    this.meterRegistry = meterRegistry;
  }

  @Transactional
  public List<EmailAnalysis> processEmails() {
    return processEmails(Integer.MAX_VALUE);
  }

  @Transactional
  public List<EmailAnalysis> processEmails(int maxEmails) {
    return recordLatency("processEmails", () -> {
      if (maxEmails <= 0) {
        return List.of();
      }
      List<FetchedEmailDto> emails = emailService.fetchEmails(maxEmails);
      return processMessages(emails);
    });
  }

  @Transactional
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
        List<EmailAnalysis> processedAnalyses = new ArrayList<>();

        for (FetchedEmailDto email : emails) {
            try {
                String emailId = email.getEmailId();

                // Idempotency check
                if (repository.existsById(emailId)) {
                    log.info("Email {} already processed. Skipping.", emailId);
                    continue;
                }

                String subject = email.getSubject();
                String sender = email.getSender() != null ? email.getSender() : "";
                String content = email.getContent() != null ? email.getContent() : "";

                LocalDateTime emailDate = email.getEmailDate();

                log.info("Analyzing email from: {}", sender);

                EmailAnalysisResult result = aiService.analyzeEmail(emailId, subject, sender, content);
                result.resolveEmailDate(emailDate);

                // Save to DB
                EmailAnalysis savedAnalysis = saveAnalysis(result);
                processedAnalyses.add(savedAnalysis);

                log.info("Analysis Complete for email {}: Score {}", emailId, result.getCriticalityScore());

            } catch (Exception e) {
                log.error("Analysis error for an email: {}", e.getMessage());
            }
        }
        return processedAnalyses;
    }

    private EmailAnalysis saveAnalysis(EmailAnalysisResult result) {
        EmailAnalysis entity = EmailAnalysis.builder()
                .emailId(result.getEmailId())
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
