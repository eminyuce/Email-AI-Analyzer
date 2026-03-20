package com.logilink.emailanalyzer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "email_analysis")
public class EmailAnalysis {

    @Id
    @Column(name = "email_id")
    private String emailId;

    @Column(name = "email_date")
    private LocalDateTime emailDate;

    @Column(name = "processed_at")
    @CreationTimestamp
    private LocalDateTime processedAt;

    private String subject;
    private String sender;

    @Column(name = "criticality_score")
    private Integer criticalityScore;

    @Column(name = "criticality_level")
    private String criticalityLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Breakdown breakdown;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_risks", columnDefinition = "jsonb")
    private List<String> keyRisks;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "affected_stakeholders", columnDefinition = "jsonb")
    private List<String> affectedStakeholders;

    @Column(name = "action_needed")
    private Boolean actionNeeded;

    @Column(name = "recommended_action", columnDefinition = "TEXT")
    private String recommendedAction;

    @Column(name = "estimated_response_time")
    private String estimatedResponseTime;

    private Integer confidence;

    public EmailAnalysis() {
    }

    public static EmailAnalysisBuilder builder() {
        return new EmailAnalysisBuilder();
    }

    public static class EmailAnalysisBuilder {
        private EmailAnalysis e = new EmailAnalysis();

        public EmailAnalysisBuilder emailId(String id) {
            e.setEmailId(id);
            return this;
        }

        public EmailAnalysisBuilder emailDate(LocalDateTime d) {
            e.setEmailDate(d);
            return this;
        }

        public EmailAnalysisBuilder subject(String s) {
            e.setSubject(s);
            return this;
        }

        public EmailAnalysisBuilder sender(String s) {
            e.setSender(s);
            return this;
        }

        public EmailAnalysisBuilder criticalityScore(Integer s) {
            e.setCriticalityScore(s);
            return this;
        }

        public EmailAnalysisBuilder criticalityLevel(String l) {
            e.setCriticalityLevel(l);
            return this;
        }

        public EmailAnalysisBuilder breakdown(Breakdown b) {
            e.setBreakdown(b);
            return this;
        }

        public EmailAnalysisBuilder summary(String s) {
            e.setSummary(s);
            return this;
        }

        public EmailAnalysisBuilder keyRisks(List<String> r) {
            e.setKeyRisks(r);
            return this;
        }

        public EmailAnalysisBuilder affectedStakeholders(List<String> s) {
            e.setAffectedStakeholders(s);
            return this;
        }

        public EmailAnalysisBuilder actionNeeded(Boolean a) {
            e.setActionNeeded(a);
            return this;
        }

        public EmailAnalysisBuilder recommendedAction(String a) {
            e.setRecommendedAction(a);
            return this;
        }

        public EmailAnalysisBuilder estimatedResponseTime(String t) {
            e.setEstimatedResponseTime(t);
            return this;
        }

        public EmailAnalysisBuilder confidence(Integer c) {
            e.setConfidence(c);
            return this;
        }

        public EmailAnalysis build() {
            return e;
        }
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmailAnalysis that)) return false;
        return emailId != null && emailId.equals(that.emailId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
