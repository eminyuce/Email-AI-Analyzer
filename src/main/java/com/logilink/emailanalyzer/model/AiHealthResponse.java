package com.logilink.emailanalyzer.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AiHealthResponse {

    private boolean success;
    private String message;
    private String expectedToken;
    private boolean containsExpectedToken;
    private long durationMs;
    private String response;
}
