package com.logilink.emailanalyzer.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DefaultSettingsTestResponse {

    private boolean success;
    private String message;
    private boolean schedulerRunning;
    private String mailUsername;
    private String llmModel;
    private boolean schedulerCronChanged;
    private int requestedEmailCount;
    private int analyzedEmailCount;
    private LocalDateTime rangeStart;
    private LocalDateTime rangeEnd;
    private List<EmailAnalysisReportDto> analyzedEmailReports = new ArrayList<>();

    public DefaultSettingsTestResponse() {
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isSchedulerRunning() {
        return schedulerRunning;
    }

    public void setSchedulerRunning(boolean schedulerRunning) {
        this.schedulerRunning = schedulerRunning;
    }

    public String getMailUsername() {
        return mailUsername;
    }

    public void setMailUsername(String mailUsername) {
        this.mailUsername = mailUsername;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public boolean isSchedulerCronChanged() {
        return schedulerCronChanged;
    }

    public void setSchedulerCronChanged(boolean schedulerCronChanged) {
        this.schedulerCronChanged = schedulerCronChanged;
    }

    public int getRequestedEmailCount() {
        return requestedEmailCount;
    }

    public void setRequestedEmailCount(int requestedEmailCount) {
        this.requestedEmailCount = requestedEmailCount;
    }

    public int getAnalyzedEmailCount() {
        return analyzedEmailCount;
    }

    public void setAnalyzedEmailCount(int analyzedEmailCount) {
        this.analyzedEmailCount = analyzedEmailCount;
    }

    public LocalDateTime getRangeStart() {
        return rangeStart;
    }

    public void setRangeStart(LocalDateTime rangeStart) {
        this.rangeStart = rangeStart;
    }

    public LocalDateTime getRangeEnd() {
        return rangeEnd;
    }

    public void setRangeEnd(LocalDateTime rangeEnd) {
        this.rangeEnd = rangeEnd;
    }

    public List<EmailAnalysisReportDto> getAnalyzedEmailReports() {
        return analyzedEmailReports;
    }

    public void setAnalyzedEmailReports(List<EmailAnalysisReportDto> analyzedEmailReports) {
        this.analyzedEmailReports = analyzedEmailReports;
    }
}
