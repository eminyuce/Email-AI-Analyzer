package com.logilink.emailanalyzer.model;

import com.logilink.emailanalyzer.domain.Breakdown;
import com.logilink.emailanalyzer.domain.EmailAnalysis;

import java.time.LocalDateTime;
import java.util.List;

public class EmailAnalysisReportDto {

    private String emailId;
    private LocalDateTime emailDate;
    private LocalDateTime processedAt;
    private String subject;
    private String sender;
    private Integer criticalityScore;
    private String criticalityLevel;
    private Breakdown breakdown;
    private String summary;
    private List<String> keyRisks;
    private List<String> affectedStakeholders;
    private Boolean actionNeeded;
    private String recommendedAction;
    private String estimatedResponseTime;
    private Integer confidence;

    public EmailAnalysisReportDto() {
    }

    public static EmailAnalysisReportDto from(EmailAnalysis entity) {
        EmailAnalysisReportDto dto = new EmailAnalysisReportDto();
        dto.setEmailId(entity.getEmailId());
        dto.setEmailDate(entity.getEmailDate());
        dto.setProcessedAt(entity.getProcessedAt());
        dto.setSubject(entity.getSubject());
        dto.setSender(entity.getSender());
        dto.setCriticalityScore(entity.getCriticalityScore());
        dto.setCriticalityLevel(entity.getCriticalityLevel());
        dto.setBreakdown(entity.getBreakdown());
        dto.setSummary(entity.getSummary());
        dto.setKeyRisks(entity.getKeyRisks());
        dto.setAffectedStakeholders(entity.getAffectedStakeholders());
        dto.setActionNeeded(entity.getActionNeeded());
        dto.setRecommendedAction(entity.getRecommendedAction());
        dto.setEstimatedResponseTime(entity.getEstimatedResponseTime());
        dto.setConfidence(entity.getConfidence());
        return dto;
    }

    public String getEmailId() {
        return emailId;
    }

    public void setEmailId(String emailId) {
        this.emailId = emailId;
    }

    public LocalDateTime getEmailDate() {
        return emailDate;
    }

    public void setEmailDate(LocalDateTime emailDate) {
        this.emailDate = emailDate;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public Integer getCriticalityScore() {
        return criticalityScore;
    }

    public void setCriticalityScore(Integer criticalityScore) {
        this.criticalityScore = criticalityScore;
    }

    public String getCriticalityLevel() {
        return criticalityLevel;
    }

    public void setCriticalityLevel(String criticalityLevel) {
        this.criticalityLevel = criticalityLevel;
    }

    public Breakdown getBreakdown() {
        return breakdown;
    }

    public void setBreakdown(Breakdown breakdown) {
        this.breakdown = breakdown;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getKeyRisks() {
        return keyRisks;
    }

    public void setKeyRisks(List<String> keyRisks) {
        this.keyRisks = keyRisks;
    }

    public List<String> getAffectedStakeholders() {
        return affectedStakeholders;
    }

    public void setAffectedStakeholders(List<String> affectedStakeholders) {
        this.affectedStakeholders = affectedStakeholders;
    }

    public Boolean getActionNeeded() {
        return actionNeeded;
    }

    public void setActionNeeded(Boolean actionNeeded) {
        this.actionNeeded = actionNeeded;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public void setRecommendedAction(String recommendedAction) {
        this.recommendedAction = recommendedAction;
    }

    public String getEstimatedResponseTime() {
        return estimatedResponseTime;
    }

    public void setEstimatedResponseTime(String estimatedResponseTime) {
        this.estimatedResponseTime = estimatedResponseTime;
    }

    public Integer getConfidence() {
        return confidence;
    }

    public void setConfidence(Integer confidence) {
        this.confidence = confidence;
    }
}
