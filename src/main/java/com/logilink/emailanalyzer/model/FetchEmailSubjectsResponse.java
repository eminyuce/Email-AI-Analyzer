package com.logilink.emailanalyzer.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class FetchEmailSubjectsResponse {

    private boolean success;
    private String message;
    private String mailboxHost;
    private int requestedMaxEmails;
    private int fetchedCount;
    private String startDate;
    private String endDate;
    private List<EmailSubjectDto> subjects = new ArrayList<>();
}
