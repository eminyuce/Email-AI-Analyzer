package com.logilink.emailanalyzer.model;

public class SchedulerControlResponse {

    private boolean success;
    private boolean running;
    private String message;

    public SchedulerControlResponse() {
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
