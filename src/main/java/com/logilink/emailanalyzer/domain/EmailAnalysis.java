package com.logilink.emailanalyzer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "email_analysis")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class EmailAnalysis {

    @Id
    @Column(name = "email_id")
    @EqualsAndHashCode.Include
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
    @Column(columnDefinition = "json")
    private Breakdown breakdown;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_risks", columnDefinition = "json")
    private List<String> keyRisks;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "affected_stakeholders", columnDefinition = "json")
    private List<String> affectedStakeholders;

    @Column(name = "action_needed")
    private Boolean actionNeeded;

    @Column(name = "recommended_action", columnDefinition = "TEXT")
    private String recommendedAction;

    @Column(name = "estimated_response_time")
    private String estimatedResponseTime;

    private Integer confidence;
}
