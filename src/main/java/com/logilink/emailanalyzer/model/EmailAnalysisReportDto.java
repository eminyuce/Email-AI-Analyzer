package com.logilink.emailanalyzer.model;

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

    private String emailId;
    private LocalDateTime emailDate;
    private LocalDateTime processedAt;
    private String subject;
    private String sender;
    private Integer criticalityScore;
    private String criticalityLevel;
    private Breakdown breakdown;
    private String summary;
    private List<String> keyRisks;
    private List<String> affectedStakeholders;
    private Boolean actionNeeded;
    private String recommendedAction;
    private String estimatedResponseTime;
    private Integer confidence;
}
