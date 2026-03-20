package com.logilink.emailanalyzer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.logilink.emailanalyzer.domain.Breakdown;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class EmailAnalysisResult {
    @JsonProperty("email_id")
    private String emailId;

    @JsonProperty("email_date")
    private LocalDateTime emailDate;

    @JsonProperty("subject")
    private String subject;

    @JsonProperty("sender")
    private String sender;

    @JsonProperty("criticality_score")
    private Integer criticalityScore;

    @JsonProperty("criticality_level")
    private String criticalityLevel;

    @JsonProperty("breakdown")
    private Breakdown breakdown;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("key_risks")
    private List<String> keyRisks;

    @JsonProperty("affected_stakeholders")
    private List<String> affectedStakeholders;

    @JsonProperty("action_needed")
    private Boolean actionNeeded;

    @JsonProperty("recommended_action")
    private String recommendedAction;

    @JsonProperty("estimated_response_time")
    private String estimatedResponseTime;

    @JsonProperty("confidence")
    private Integer confidence;
}
