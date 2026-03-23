package com.logilink.emailanalyzer.service;

import com.logilink.emailanalyzer.common.AppConstants;
import com.logilink.emailanalyzer.domain.EmailAnalysis;
import com.logilink.emailanalyzer.mapper.EmailAnalysisMapper;
import com.logilink.emailanalyzer.model.CoreRangeAnalysisResponse;
import com.logilink.emailanalyzer.model.EmailAnalysisReportDto;
import com.logilink.emailanalyzer.model.EmailAnalysisResult;
import com.logilink.emailanalyzer.repository.EmailAnalysisRepository;
import jakarta.mail.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class CoreRangeAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(CoreRangeAnalysisService.class);

    private final EmailService emailService;
    private final AIService aiService;
    private final EmailAnalysisRepository emailAnalysisRepository;
    private final EmailAnalysisMapper emailAnalysisMapper;

    public CoreRangeAnalysisService(
            EmailService emailService,
            AIService aiService,
            EmailAnalysisRepository emailAnalysisRepository,
            EmailAnalysisMapper emailAnalysisMapper
    ) {
        this.emailService = emailService;
        this.aiService = aiService;
        this.emailAnalysisRepository = emailAnalysisRepository;
        this.emailAnalysisMapper = emailAnalysisMapper;
    }

    public CoreRangeAnalysisResponse analyzeByDateRange(LocalDateTime startDate, LocalDateTime endDate, int maxEmails) {
        String defaultSystemPrompt = loadDefaultSystemPrompt();
        Date rangeStart = Date.from(startDate.atZone(ZoneId.systemDefault()).toInstant());
        Date rangeEnd = Date.from(endDate.atZone(ZoneId.systemDefault()).toInstant());
        log.info("Starting core range analysis for {} emails from {} to {}", maxEmails, startDate, endDate);

        List<Message> messages = emailService.fetchEmailsByRange(maxEmails, rangeStart, rangeEnd);
        List<EmailAnalysisReportDto> reports = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int skippedCount = 0;
        int analyzedCount = 0;
        log.info("Fetched {} emails from {} to {}", messages.size(), startDate, endDate);
        for (Message message : messages) {
            String emailId = "unknown";
            try {
                emailId = emailService.getEmailId(message);
                if (emailAnalysisRepository.existsById(emailId)) {
                    skippedCount++;
                    continue;
                }

                String subject = message.getSubject();
                String sender = message.getFrom() != null && message.getFrom().length > 0
                        ? message.getFrom()[0].toString()
                        : "";
                String content = emailService.getTextFromMessage(message);

                EmailAnalysisResult result = aiService.analyzeEmail(emailId, subject, sender, content, defaultSystemPrompt);
                log.info("Core range analysis result for email {}: {}", emailId, result);
                enrichResultFromMessage(result, message, emailId, subject, sender);
                EmailAnalysis saved = emailAnalysisRepository.save(toEntity(result));
                reports.add(emailAnalysisMapper.toReportDto(saved));
                analyzedCount++;
            } catch (Exception e) {
                log.error("Core range analysis failed for email {}: {}", emailId, e.getMessage());
                errors.add("Email " + emailId + " failed: " + e.getMessage());
            }
        }

        CoreRangeAnalysisResponse response = new CoreRangeAnalysisResponse();
        response.setSuccess(errors.isEmpty());
        response.setMessage(errors.isEmpty()
                ? "End-to-end date-range analysis completed successfully."
                : "End-to-end date-range analysis completed with some failures.");
        response.setSystemPromptPath(AppConstants.Defaults.DEFAULT_SYSTEM_PROMPT_PATH);
        response.setRequestedMaxEmails(maxEmails);
        response.setFetchedEmailCount(messages.size());
        response.setAnalyzedEmailCount(analyzedCount);
        response.setSavedEmailCount(reports.size());
        response.setSkippedEmailCount(skippedCount);
        response.setRangeStart(startDate);
        response.setRangeEnd(endDate);
        response.setAnalyzedEmailReports(reports);
        response.setErrors(errors);
        log.info("Core range analysis completed with {} analyzed emails, {} skipped emails, and {} errors", analyzedCount, skippedCount, errors.size());
        return response;
    }

    private void enrichResultFromMessage(
            EmailAnalysisResult result,
            Message message,
            String emailId,
            String subject,
            String sender
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
        if (result.getEmailDate() == null) {
            result.setEmailDate(extractMessageDate(message));
        }
    }

    private LocalDateTime extractMessageDate(Message message) {
        try {
            Date sentDate = message.getSentDate();
            if (sentDate != null) {
                return sentDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            }
            Date receivedDate = message.getReceivedDate();
            if (receivedDate != null) {
                return receivedDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            }
        } catch (Exception e) {
            log.warn("Failed to extract message date: {}", e.getMessage());
        }
        return null;
    }

    private EmailAnalysis toEntity(EmailAnalysisResult result) {
        return EmailAnalysis.builder()
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
    }

    private String loadDefaultSystemPrompt() {
        ClassPathResource resource = new ClassPathResource(AppConstants.Defaults.DEFAULT_SYSTEM_PROMPT_PATH);
        try (var inputStream = resource.getInputStream()) {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (content.isBlank()) {
                throw new IllegalStateException(
                        "Default system prompt file is empty: " + AppConstants.Defaults.DEFAULT_SYSTEM_PROMPT_PATH
                );
            }
            return content;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot read default system prompt file: " + AppConstants.Defaults.DEFAULT_SYSTEM_PROMPT_PATH,
                    e
            );
        }
    }
}
