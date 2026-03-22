package com.logilink.emailanalyzer.model;

public class EmailSubjectDto {

    private String subject;
    private String receivedAt;
    private String from;

    public EmailSubjectDto() {
    }

    public EmailSubjectDto(String subject, String receivedAt, String from) {
        this.subject = subject;
        this.receivedAt = receivedAt;
        this.from = from;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(String receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }
}
