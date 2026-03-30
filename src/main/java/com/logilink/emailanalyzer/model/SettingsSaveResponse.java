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
public class SettingsSaveResponse {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("message")
    private String message;

    @JsonProperty("scheduler_running")
    @JsonAlias("schedulerRunning")
    private boolean schedulerRunning;

    @JsonProperty("errors")
    private List<ApiValidationError> errors = new ArrayList<>();
}
