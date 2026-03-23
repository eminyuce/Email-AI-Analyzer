package com.logilink.emailanalyzer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AppSettings {

    public static final Long SINGLETON_ID = 1L;

    @Id
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
}
