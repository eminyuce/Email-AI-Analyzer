package com.logilink.emailanalyzer.exception;

public class EmailAnalysisException extends RuntimeException {
    public EmailAnalysisException(String message) {
        super(message);
    }

    public EmailAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
