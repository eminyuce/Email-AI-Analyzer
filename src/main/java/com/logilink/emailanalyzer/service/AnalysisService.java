package com.logilink.emailanalyzer.service;

import com.logilink.emailanalyzer.domain.EmailAnalysis;
import com.logilink.emailanalyzer.model.EmailAnalysisResult;
import com.logilink.emailanalyzer.repository.EmailAnalysisRepository;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final EmailService emailService;
    private final AIService aiService;
    private final EmailAnalysisRepository repository;

    public AnalysisService(EmailService emailService, AIService aiService, EmailAnalysisRepository repository) {
        this.emailService = emailService;
        this.aiService = aiService;
        this.repository = repository;
    }

    @Transactional
    public void processEmails() {
        List<Message> messages = emailService.fetchUnreadEmails();
        log.info("Found {} unread emails to analyze.", messages.size());

        for (Message message : messages) {
            try {
                String emailId = emailService.getEmailId(message);

                // Idempotency check
                if (repository.existsById(emailId)) {
                    log.info("Email {} already processed. Skipping.", emailId);
                    continue;
                }

                String subject = message.getSubject();
                String sender = message.getFrom()[0].toString();
                String content = emailService.getTextFromMessage(message);

                LocalDateTime emailDate = message.getSentDate().toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();

                log.info("Analyzing email from: {}", sender);

                EmailAnalysisResult result = aiService.analyzeEmail(emailId, subject, sender, content);

                // Ensure email date is set from the message if LLM doesn't provide it correctly
                if (result.getEmailDate() == null) {
                    result.setEmailDate(emailDate);
                }

                // Save to DB
                saveAnalysis(result);

                log.info("Analysis Complete for email {}: Score {}", emailId, result.getCriticalityScore());

            } catch (MessagingException | IOException e) {
                log.error("Failed to process message: {}", e.getMessage());
            } catch (Exception e) {
                log.error("Analysis error for an email: {}", e.getMessage());
            }
        }
    }

    private void saveAnalysis(EmailAnalysisResult result) {
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

        repository.save(entity);
    }
}
