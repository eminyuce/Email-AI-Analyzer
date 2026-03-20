package com.logilink.emailanalyzer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.logilink.emailanalyzer.domain.Breakdown;

import java.time.LocalDateTime;
import java.util.List;

public class EmailAnalysisResult {
    @JsonProperty("email_id")
    private String emailId;

    @JsonProperty("email_date")
    private LocalDateTime emailDate;

    @JsonProperty("subject")
    private String subject;

    @JsonProperty("sender")
    private String sender;

    @JsonProperty("criticality_score")
    private Integer criticalityScore;

    @JsonProperty("criticality_level")
    private String criticalityLevel;

    @JsonProperty("breakdown")
    private Breakdown breakdown;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("key_risks")
    private List<String> keyRisks;

    @JsonProperty("affected_stakeholders")
    private List<String> affectedStakeholders;

    @JsonProperty("action_needed")
    private Boolean actionNeeded;

    @JsonProperty("recommended_action")
    private String recommendedAction;

    @JsonProperty("estimated_response_time")
    private String estimatedResponseTime;

    @JsonProperty("confidence")
    private Integer confidence;

    public EmailAnalysisResult() {
    }

    // Getters and Setters
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
