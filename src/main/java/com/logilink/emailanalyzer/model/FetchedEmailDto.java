package com.logilink.emailanalyzer.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * In-memory snapshot of an email loaded while the IMAP folder is still open. Safe to use after the
 * store/folder connection is closed.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FetchedEmailDto {

    @JsonProperty("email_id")
    @JsonAlias("emailId")
    private String emailId;

    private String subject;

    @JsonProperty("senders")
    private List<String> senders;

    @JsonProperty("recipients_to")
    @JsonAlias("recipientsTo")
    private List<String> recipientsTo;

    @JsonProperty("recipients_cc")
    @JsonAlias("recipientsCc")
    private List<String> recipientsCc;

    private String content;

    @JsonProperty("email_date")
    @JsonAlias("emailDate")
    private LocalDateTime emailDate;

    @JsonProperty("received_at")
    @JsonAlias("receivedAt")
    private Instant receivedAt;

    @JsonProperty("in_reply_to")
    @JsonAlias("inReplyTo")
    private String inReplyTo;

    private String references;

    private List<AttachmentDto> attachments;

    /**
     * From addresses as one string for AI prompts and {@code EmailAnalysis.sender} (comma-separated if
     * several).
     */
    public String getSenderLine() {
        if (CollectionUtils.isEmpty(senders)) {
            return "";
        }
        return String.join(", ", senders);
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentDto {

        @JsonProperty("file_name")
        @JsonAlias("fileName")
        private String fileName;

        @JsonProperty("content_type")
        @JsonAlias("contentType")
        private String contentType;

        private byte[] data;

        private long size;
    }
}
