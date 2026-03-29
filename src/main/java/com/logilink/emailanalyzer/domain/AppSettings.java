package com.logilink.emailanalyzer.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "app_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AppSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "mail_host")
    private String mailHost;

    @Column(name = "mail_port")
    private Integer mailPort;

    @Column(name = "mail_username")
    private String mailUsername;

    @Column(name = "mail_password")
    private String mailPassword;

    @Column(name = "mail_ssl_enabled")
    private Boolean mailSslEnabled;

    @Lob
    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "db_host")
    private String dbHost;

    @Column(name = "db_port")
    private Integer dbPort;

    @Column(name = "db_name")
    private String dbName;

    @Column(name = "db_username")
    private String dbUsername;

    @Column(name = "db_password")
    private String dbPassword;

    @Column(name = "llm_provider")
    private String llmProvider;

    @Column(name = "llm_model")
    private String llmModel;

    @Column(name = "llm_url")
    private String llmUrl;

    @Column(name = "llm_temperature")
    private Double llmTemperature;

    @Column(name = "scheduler_enabled")
    private Boolean schedulerEnabled;

    @Column(name = "scheduler_cron")
    private String schedulerCron;

    @Column(name = "scheduler_date_range_days")
    private Integer schedulerDateRangeDays;

    @Column(name = "scheduler_max_emails")
    private Integer schedulerMaxEmails;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant updatedAt;

    @PrePersist
    void prePersistTimestamps() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdateTimestamp() {
        updatedAt = Instant.now();
    }
}
