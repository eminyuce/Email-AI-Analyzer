package com.logilink.emailanalyzer.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AiHealthResponse {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("message")
    private String message;

    @JsonProperty("expected_token")
    @JsonAlias("expectedToken")
    private String expectedToken;

    @JsonProperty("contains_expected_token")
    @JsonAlias("containsExpectedToken")
    private boolean containsExpectedToken;

    @JsonProperty("duration_ms")
    @JsonAlias("durationMs")
    private long durationMs;

    @JsonProperty("response")
    private String response;
}
