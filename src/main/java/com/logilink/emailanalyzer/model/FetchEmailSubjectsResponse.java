package com.logilink.emailanalyzer.model;

import java.util.ArrayList;
import java.util.List;

public class FetchEmailSubjectsResponse {

    private boolean success;
    private String message;
    private String mailboxHost;
    private int requestedMaxEmails;
    private int fetchedCount;
    private String startDate;
    private String endDate;
    private List<EmailSubjectDto> subjects = new ArrayList<>();

    public FetchEmailSubjectsResponse() {
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

    public String getMailboxHost() {
        return mailboxHost;
    }

    public void setMailboxHost(String mailboxHost) {
        this.mailboxHost = mailboxHost;
    }

    public int getRequestedMaxEmails() {
        return requestedMaxEmails;
    }

    public void setRequestedMaxEmails(int requestedMaxEmails) {
        this.requestedMaxEmails = requestedMaxEmails;
    }

    public int getFetchedCount() {
        return fetchedCount;
    }

    public void setFetchedCount(int fetchedCount) {
        this.fetchedCount = fetchedCount;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public List<EmailSubjectDto> getSubjects() {
        return subjects;
    }

    public void setSubjects(List<EmailSubjectDto> subjects) {
        this.subjects = subjects;
    }
}
