package com.logilink.emailanalyzer.model;

public class AiHealthResponse {

    private boolean success;
    private String message;
    private String expectedToken;
    private boolean containsExpectedToken;
    private long durationMs;
    private String response;

    public AiHealthResponse() {
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

    public String getExpectedToken() {
        return expectedToken;
    }

    public void setExpectedToken(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    public boolean isContainsExpectedToken() {
        return containsExpectedToken;
    }

    public void setContainsExpectedToken(boolean containsExpectedToken) {
        this.containsExpectedToken = containsExpectedToken;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }
}
