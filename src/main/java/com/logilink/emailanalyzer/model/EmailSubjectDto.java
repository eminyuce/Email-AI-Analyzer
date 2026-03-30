package com.logilink.emailanalyzer.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmailSubjectDto {

    @JsonProperty("subject")
    private String subject;

    @JsonProperty("received_at")
    @JsonAlias("receivedAt")
    private String receivedAt;

    @JsonProperty("from")
    private String from;
}
