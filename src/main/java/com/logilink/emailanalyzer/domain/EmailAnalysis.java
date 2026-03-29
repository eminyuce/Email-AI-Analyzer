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
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class EmailAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "email_id")
    @EqualsAndHashCode.Include
    private String emailId;

    /**
     * {@link com.logilink.emailanalyzer.domain.AppSettings#getId()} of the profile used when this row was created.
     */
    @Column(name = "setting_id")
    private Long settingId;

    @Column(name = "email_date")
    private LocalDateTime emailDate;

    @Column(name = "processed_at")
    @CreationTimestamp
    private LocalDateTime processedAt;

    private String subject;
    private String sender;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "in_reply_to", columnDefinition = "TEXT")
    private String inReplyTo;

    /**
     * Value of the RFC 5322 References header (message-id chain).
     */
    @Column(name = "email_references", columnDefinition = "TEXT")
    private String emailReferences;

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
