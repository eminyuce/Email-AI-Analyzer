package com.logilink.emailanalyzer.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "email_analysis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
