package com.logilink.emailanalyzer.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "app_settings")
public class AppSettings {

    public static final Long SINGLETON_ID = 1L;

    @Id
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

    public AppSettings() {
    }

    public static AppSettingsBuilder builder() {
        return new AppSettingsBuilder();
    }

    public static class AppSettingsBuilder {
        private AppSettings settings = new AppSettings();

        public AppSettingsBuilder id(Long id) {
            settings.setId(id);
            return this;
        }

        public AppSettingsBuilder mailHost(String mailHost) {
            settings.setMailHost(mailHost);
            return this;
        }

        public AppSettingsBuilder mailPort(Integer mailPort) {
            settings.setMailPort(mailPort);
            return this;
        }

        public AppSettingsBuilder mailUsername(String mailUsername) {
            settings.setMailUsername(mailUsername);
            return this;
        }

        public AppSettingsBuilder mailPassword(String mailPassword) {
            settings.setMailPassword(mailPassword);
            return this;
        }

        public AppSettingsBuilder mailSslEnabled(Boolean mailSslEnabled) {
            settings.setMailSslEnabled(mailSslEnabled);
            return this;
        }

        public AppSettingsBuilder systemPrompt(String systemPrompt) {
            settings.setSystemPrompt(systemPrompt);
            return this;
        }

        public AppSettingsBuilder dbHost(String dbHost) {
            settings.setDbHost(dbHost);
            return this;
        }

        public AppSettingsBuilder dbPort(Integer dbPort) {
            settings.setDbPort(dbPort);
            return this;
        }

        public AppSettingsBuilder dbName(String dbName) {
            settings.setDbName(dbName);
            return this;
        }

        public AppSettingsBuilder dbUsername(String dbUsername) {
            settings.setDbUsername(dbUsername);
            return this;
        }

        public AppSettingsBuilder dbPassword(String dbPassword) {
            settings.setDbPassword(dbPassword);
            return this;
        }

        public AppSettingsBuilder llmModel(String llmModel) {
            settings.setLlmModel(llmModel);
            return this;
        }

        public AppSettingsBuilder llmUrl(String llmUrl) {
            settings.setLlmUrl(llmUrl);
            return this;
        }

        public AppSettingsBuilder llmTemperature(Double llmTemperature) {
            settings.setLlmTemperature(llmTemperature);
            return this;
        }

        public AppSettingsBuilder schedulerEnabled(Boolean schedulerEnabled) {
            settings.setSchedulerEnabled(schedulerEnabled);
            return this;
        }

        public AppSettingsBuilder schedulerCron(String schedulerCron) {
            settings.setSchedulerCron(schedulerCron);
            return this;
        }

        public AppSettingsBuilder schedulerDateRangeDays(Integer schedulerDateRangeDays) {
            settings.setSchedulerDateRangeDays(schedulerDateRangeDays);
            return this;
        }

        public AppSettingsBuilder schedulerMaxEmails(Integer schedulerMaxEmails) {
            settings.setSchedulerMaxEmails(schedulerMaxEmails);
            return this;
        }

        public AppSettings build() {
            return settings;
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMailHost() {
        return mailHost;
    }

    public void setMailHost(String mailHost) {
        this.mailHost = mailHost;
    }

    public Integer getMailPort() {
        return mailPort;
    }

    public void setMailPort(Integer mailPort) {
        this.mailPort = mailPort;
    }

    public String getMailUsername() {
        return mailUsername;
    }

    public void setMailUsername(String mailUsername) {
        this.mailUsername = mailUsername;
    }

    public String getMailPassword() {
        return mailPassword;
    }

    public void setMailPassword(String mailPassword) {
        this.mailPassword = mailPassword;
    }

    public Boolean getMailSslEnabled() {
        return mailSslEnabled;
    }

    public void setMailSslEnabled(Boolean mailSslEnabled) {
        this.mailSslEnabled = mailSslEnabled;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getDbHost() {
        return dbHost;
    }

    public void setDbHost(String dbHost) {
        this.dbHost = dbHost;
    }

    public Integer getDbPort() {
        return dbPort;
    }

    public void setDbPort(Integer dbPort) {
        this.dbPort = dbPort;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public String getDbUsername() {
        return dbUsername;
    }

    public void setDbUsername(String dbUsername) {
        this.dbUsername = dbUsername;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public void setDbPassword(String dbPassword) {
        this.dbPassword = dbPassword;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public String getLlmUrl() {
        return llmUrl;
    }

    public void setLlmUrl(String llmUrl) {
        this.llmUrl = llmUrl;
    }

    public Double getLlmTemperature() {
        return llmTemperature;
    }

    public void setLlmTemperature(Double llmTemperature) {
        this.llmTemperature = llmTemperature;
    }

    public Boolean getSchedulerEnabled() {
        return schedulerEnabled;
    }

    public void setSchedulerEnabled(Boolean schedulerEnabled) {
        this.schedulerEnabled = schedulerEnabled;
    }

    public String getSchedulerCron() {
        return schedulerCron;
    }

    public void setSchedulerCron(String schedulerCron) {
        this.schedulerCron = schedulerCron;
    }

    public Integer getSchedulerDateRangeDays() {
        return schedulerDateRangeDays;
    }

    public void setSchedulerDateRangeDays(Integer schedulerDateRangeDays) {
        this.schedulerDateRangeDays = schedulerDateRangeDays;
    }

    public Integer getSchedulerMaxEmails() {
        return schedulerMaxEmails;
    }

    public void setSchedulerMaxEmails(Integer schedulerMaxEmails) {
        this.schedulerMaxEmails = schedulerMaxEmails;
    }
}
