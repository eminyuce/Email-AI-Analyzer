package com.logilink.emailanalyzer.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class DefaultSettingsTestResponse {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("message")
    private String message;

    @JsonProperty("scheduler_running")
    @JsonAlias("schedulerRunning")
    private boolean schedulerRunning;

    @JsonProperty("mail_username")
    @JsonAlias("mailUsername")
    private String mailUsername;

    @JsonProperty("llm_model")
    @JsonAlias("llmModel")
    private String llmModel;

    @JsonProperty("scheduler_cron_changed")
    @JsonAlias("schedulerCronChanged")
    private boolean schedulerCronChanged;

    @JsonProperty("requested_email_count")
    @JsonAlias("requestedEmailCount")
    private int requestedEmailCount;

    @JsonProperty("analyzed_email_count")
    @JsonAlias("analyzedEmailCount")
    private int analyzedEmailCount;

    @JsonProperty("range_start")
    @JsonAlias("rangeStart")
    private LocalDateTime rangeStart;

    @JsonProperty("range_end")
    @JsonAlias("rangeEnd")
    private LocalDateTime rangeEnd;

    @JsonProperty("analyzed_email_reports")
    @JsonAlias("analyzedEmailReports")
    private List<EmailAnalysisReportDto> analyzedEmailReports = new ArrayList<>();
}
