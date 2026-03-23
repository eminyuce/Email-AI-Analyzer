package com.logilink.emailanalyzer.model;

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

    private boolean success;
    private String message;
    private String systemPromptPath;
    private int requestedMaxEmails;
    private int fetchedEmailCount;
    private int analyzedEmailCount;
    private int savedEmailCount;
    private int skippedEmailCount;
    private LocalDateTime rangeStart;
    private LocalDateTime rangeEnd;
    @Builder.Default
    private List<EmailAnalysisReportDto> analyzedEmailReports = new ArrayList<>();
    @Builder.Default
    private List<String> errors = new ArrayList<>();
}
