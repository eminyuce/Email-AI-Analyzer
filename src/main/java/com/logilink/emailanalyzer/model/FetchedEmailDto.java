package com.logilink.emailanalyzer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * In-memory snapshot of an email loaded while the IMAP folder is still open.
 * Safe to use after the store/folder connection is closed.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FetchedEmailDto {

    private String emailId;
    private String subject;
    private String sender;
    /** Extracted plain/HTML-as-text body (same rules as {@code EmailService} parsing). */
    private String content;
    /** Prefer sent date, else received; for persistence and analysis. */
    private LocalDateTime emailDate;
    /** Server received time when present (e.g. for debug / listing APIs). */
    private Instant receivedAt;
}
