package com.logilink.emailanalyzer.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SystemPromptUpdateResponse {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("message")
    private String message;

    @JsonProperty("settings_id")
    @JsonAlias("settingsId")
    private Long settingsId;

    @JsonProperty("system_prompt_length")
    @JsonAlias("systemPromptLength")
    private int systemPromptLength;
}
