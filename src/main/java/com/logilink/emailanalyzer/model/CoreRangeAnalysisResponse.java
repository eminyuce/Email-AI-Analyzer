package com.logilink.emailanalyzer.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoreRangeAnalysisResponse {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("message")
    private String message;

    @JsonProperty("system_prompt_path")
    @JsonAlias("systemPromptPath")
    private String systemPromptPath;

    @JsonProperty("requested_max_emails")
    @JsonAlias("requestedMaxEmails")
    private int requestedMaxEmails;

    @JsonProperty("fetched_email_count")
    @JsonAlias("fetchedEmailCount")
    private int fetchedEmailCount;

    @JsonProperty("analyzed_email_count")
    @JsonAlias("analyzedEmailCount")
    private int analyzedEmailCount;

    @JsonProperty("saved_email_count")
    @JsonAlias("savedEmailCount")
    private int savedEmailCount;

    @JsonProperty("skipped_email_count")
    @JsonAlias("skippedEmailCount")
    private int skippedEmailCount;

    @JsonProperty("range_start")
    @JsonAlias("rangeStart")
    private LocalDateTime rangeStart;

    @JsonProperty("range_end")
    @JsonAlias("rangeEnd")
    private LocalDateTime rangeEnd;

    @Builder.Default
    @JsonProperty("analyzed_email_reports")
    @JsonAlias("analyzedEmailReports")
    private List<EmailAnalysisReportDto> analyzedEmailReports = new ArrayList<>();

    @Builder.Default
    @JsonProperty("errors")
    private List<String> errors = new ArrayList<>();
}
