package com.logilink.emailanalyzer.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.logilink.emailanalyzer.domain.Breakdown;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class EmailAnalysisReportDto {

    @JsonProperty("email_id")
    @JsonAlias("emailId")
    private String emailId;

    @JsonProperty("setting_id")
    @JsonAlias("settingId")
    private Long settingId;

    @JsonProperty("email_date")
    @JsonAlias("emailDate")
    private LocalDateTime emailDate;

    @JsonProperty("processed_at")
    @JsonAlias("processedAt")
    private LocalDateTime processedAt;

    @JsonProperty("subject")
    private String subject;

    @JsonProperty("sender")
    private String sender;

    @JsonProperty("content")
    private String content;

    @JsonProperty("in_reply_to")
    @JsonAlias("inReplyTo")
    private String inReplyTo;

    @JsonProperty("email_references")
    @JsonAlias("emailReferences")
    private String emailReferences;

    @JsonProperty("criticality_score")
    @JsonAlias("criticalityScore")
    private Integer criticalityScore;

    @JsonProperty("criticality_level")
    @JsonAlias("criticalityLevel")
    private String criticalityLevel;

    @JsonProperty("breakdown")
    private Breakdown breakdown;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("key_risks")
    @JsonAlias("keyRisks")
    private List<String> keyRisks;

    @JsonProperty("affected_stakeholders")
    @JsonAlias("affectedStakeholders")
    private List<String> affectedStakeholders;

    @JsonProperty("action_needed")
    @JsonAlias("actionNeeded")
    private Boolean actionNeeded;

    @JsonProperty("recommended_action")
    @JsonAlias("recommendedAction")
    private String recommendedAction;

    @JsonProperty("estimated_response_time")
    @JsonAlias("estimatedResponseTime")
    private String estimatedResponseTime;

    @JsonProperty("confidence")
    private Integer confidence;
}
