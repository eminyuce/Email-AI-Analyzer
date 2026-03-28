package com.logilink.emailanalyzer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

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
    private List<String> senders;      // Changed from String
    private List<String> recipientsTo;  // Added
    private List<String> recipientsCc;  // Added
    private String content;
    private LocalDateTime emailDate;
    private Instant receivedAt;
    private String inReplyTo;
    private String references;
    private List<AttachmentDto> attachments;

    /**
     * From addresses as one string for AI prompts and {@code EmailAnalysis.sender} (comma-separated if several).
     */
    public String getSenderLine() {
        if (senders == null || senders.isEmpty()) {
            return "";
        }
        return String.join(", ", senders);
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentDto {
        private String fileName;
        private String contentType;
        private byte[] data;
        private long size;
    }
}
