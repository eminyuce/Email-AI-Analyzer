package com.logilink.emailanalyzer.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class FetchEmailSubjectsResponse {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("message")
    private String message;

    @JsonProperty("mailbox_host")
    @JsonAlias("mailboxHost")
    private String mailboxHost;

    @JsonProperty("requested_max_emails")
    @JsonAlias("requestedMaxEmails")
    private int requestedMaxEmails;

    @JsonProperty("fetched_count")
    @JsonAlias("fetchedCount")
    private int fetchedCount;

    @JsonProperty("start_date")
    @JsonAlias("startDate")
    private String startDate;

    @JsonProperty("end_date")
    @JsonAlias("endDate")
    private String endDate;

    @JsonProperty("subjects")
    private List<EmailSubjectDto> subjects = new ArrayList<>();
}
