package com.logilink.emailanalyzer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SchedulerControlResponse {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("running")
    private boolean running;

    @JsonProperty("message")
    private String message;
}
