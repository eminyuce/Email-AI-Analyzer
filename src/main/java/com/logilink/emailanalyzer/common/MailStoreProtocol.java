package com.logilink.emailanalyzer.common;

public enum MailStoreProtocol {
    IMAP("imap"),
    IMAPS("imaps");

    private final String value;

    MailStoreProtocol(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
