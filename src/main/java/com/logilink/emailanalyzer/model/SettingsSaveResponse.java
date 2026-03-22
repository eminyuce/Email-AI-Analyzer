package com.logilink.emailanalyzer.model;

import java.util.ArrayList;
import java.util.List;

public class SettingsSaveResponse {

    private boolean success;
    private String message;
    private boolean schedulerRunning;
    private List<ApiValidationError> errors = new ArrayList<>();

    public SettingsSaveResponse() {
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

    public boolean isSchedulerRunning() {
        return schedulerRunning;
    }

    public void setSchedulerRunning(boolean schedulerRunning) {
        this.schedulerRunning = schedulerRunning;
    }

    public List<ApiValidationError> getErrors() {
        return errors;
    }

    public void setErrors(List<ApiValidationError> errors) {
        this.errors = errors;
    }
}
