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

import java.util.ArrayList;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
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
    public List<EmailAnalysis> processEmails() {
        return processEmails(Integer.MAX_VALUE);
    }

    @Transactional
    public List<EmailAnalysis> processEmails(int maxEmails) {
        if (maxEmails <= 0) {
            return List.of();
        }

        List<Message> messages = emailService.fetchEmails(maxEmails);
        return processMessages(messages);
    }

    @Transactional
    public List<EmailAnalysis> processEmails(int maxEmails, Date startDate, Date endDate) {
        if (maxEmails <= 0) {
            return List.of();
        }
        List<Message> messages = emailService.fetchEmailsByRange(maxEmails, startDate, endDate);
        return processMessages(messages);
    }

    private List<EmailAnalysis> processMessages(List<Message> messages) {
        log.info("Found {} emails to analyze.", messages.size());
        List<EmailAnalysis> processedAnalyses = new ArrayList<>();

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
                EmailAnalysis savedAnalysis = saveAnalysis(result);
                processedAnalyses.add(savedAnalysis);

                log.info("Analysis Complete for email {}: Score {}", emailId, result.getCriticalityScore());

            } catch (MessagingException | IOException e) {
                log.error("Failed to process message: {}", e.getMessage());
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
