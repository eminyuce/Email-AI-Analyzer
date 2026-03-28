package com.logilink.emailanalyzer.service;

import com.logilink.emailanalyzer.common.MailStoreProtocol;
import com.logilink.emailanalyzer.domain.AppSettings;
import com.logilink.emailanalyzer.exception.EmailAnalysisException;
import com.logilink.emailanalyzer.model.FetchedEmailDto;
import com.logilink.emailanalyzer.model.FetchedEmailDto.AttachmentDto;
import jakarta.mail.*;
import jakarta.mail.internet.InternetHeaders;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.search.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final int DEFAULT_LOOKBACK_DAYS = 30;
    private static final int DEFAULT_IMAPS_PORT = 993;
    private static final long MAX_TOTAL_ATTACHMENT_SIZE = 200 * 1024 * 1024; // 200 MB

    private final AppSettingsService appSettingsService;
    private final int imapConnectionTimeoutMs;
    private final int imapReadTimeoutMs;
    private final int imapWriteTimeoutMs;
    private final String imapSslTrust;

    public EmailService(
            AppSettingsService appSettingsService,
            @Value("${email.imap.connection-timeout-ms:10000}") int imapConnectionTimeoutMs,
            @Value("${email.imap.timeout-ms:20000}") int imapReadTimeoutMs,
            @Value("${email.imap.write-timeout-ms:20000}") int imapWriteTimeoutMs,
            @Value("${email.imap.ssl-trust:*}") String imapSslTrust
    ) {
        this.appSettingsService = appSettingsService;
        this.imapConnectionTimeoutMs = imapConnectionTimeoutMs;
        this.imapReadTimeoutMs = imapReadTimeoutMs;
        this.imapWriteTimeoutMs = imapWriteTimeoutMs;
        this.imapSslTrust = imapSslTrust;
    }

    public List<FetchedEmailDto> fetchUnreadEmails(int maxEmails) {
        if (maxEmails <= 0) {
            return List.of();
        }

        Instant now = Instant.now();
        Date endDate = Date.from(now);
        Date startDate = Date.from(now.minus(DEFAULT_LOOKBACK_DAYS, ChronoUnit.DAYS));
        return fetchUnreadEmailsByRange(maxEmails, startDate, endDate);
    }

    public List<FetchedEmailDto> fetchUnreadEmailsByRange(int maxEmails, Date startDate, Date endDate) {
        return fetchEmailsByRange(maxEmails, startDate, endDate, true);
    }

    public List<FetchedEmailDto> fetchEmails(int maxEmails) {
        if (maxEmails <= 0) {
            return List.of();
        }

        Instant now = Instant.now();
        Date endDate = Date.from(now);
        Date startDate = Date.from(now.minus(DEFAULT_LOOKBACK_DAYS, ChronoUnit.DAYS));
        return fetchEmailsByRange(maxEmails, startDate, endDate, false);
    }

    public List<FetchedEmailDto> fetchEmailsByRange(int maxEmails, Date startDate, Date endDate) {
        return fetchEmailsByRange(maxEmails, startDate, endDate, false);
    }

    public List<FetchedEmailDto> fetchEmailsByRange(int maxEmails, Date startDate, Date endDate, boolean unreadOnly) {
        if (maxEmails <= 0) {
            return List.of();
        }
        if (startDate == null || endDate == null || startDate.after(endDate)) {
            throw new EmailAnalysisException("Invalid date range for email search.");
        }

        AppSettings settings = appSettingsService.getRequiredMailSettings();
        List<FetchedEmailDto> emails = new ArrayList<>();

        boolean preferImplicitSsl = shouldUseImplicitSsl(settings);
        try {
            emails.addAll(fetchMessages(settings, maxEmails, startDate, endDate, unreadOnly, preferImplicitSsl));
            log.debug("Fetched {} emails from {} to {} with {} unread emails", emails.size(), startDate, endDate, unreadOnly ? "only" : "all");
        } catch (Exception firstException) {
            // Retry with STARTTLS when server rejects implicit SSL to avoid SSL handshake mismatch failures.
            if (preferImplicitSsl && isUnsupportedSslMessage(firstException)) {
                log.warn("IMAPS handshake failed; retrying mailbox fetch with IMAP STARTTLS.");
                emails.clear();
                try {
                    emails.addAll(fetchMessages(settings, maxEmails, startDate, endDate, unreadOnly, false));
                    log.debug("Fetched {} emails from {} to {} with {} unread emails", emails.size(), startDate, endDate, unreadOnly ? "only" : "all");
                } catch (MessagingException retryException) {
                    throw new EmailAnalysisException("Failed to fetch emails within range", retryException);
                }
            } else {
                log.error("Error fetching emails: {}", firstException.getMessage());
                throw new EmailAnalysisException("Failed to fetch emails within range", firstException);
            }
        }
        return emails;
    }

    private List<FetchedEmailDto> fetchMessages(
            AppSettings settings,
            int maxEmails,
            Date startDate,
            Date endDate,
            boolean unreadOnly,
            boolean useImplicitSsl
    ) throws MessagingException {
        MailStoreProtocol protocol = useImplicitSsl ? MailStoreProtocol.IMAPS : MailStoreProtocol.IMAP;
        Properties properties = buildMailProperties(settings, protocol.value(), useImplicitSsl);
        Session emailSession = Session.getInstance(properties);

        Store store = null;
        Folder emailFolder = null;
        List<Message> folderMessages = new ArrayList<>();
        try {
            store = emailSession.getStore(protocol.value());
            store.connect(settings.getMailHost(), settings.getMailPort(), settings.getMailUsername(), settings.getMailPassword());

            emailFolder = store.getFolder("INBOX");
            emailFolder.open(Folder.READ_ONLY);
            SearchTerm dateRange = new AndTerm(
                    new ReceivedDateTerm(ComparisonTerm.GE, startDate),
                    new ReceivedDateTerm(ComparisonTerm.LE, endDate)
            );
            SearchTerm combinedTerm = dateRange;
            if (unreadOnly) {
                SearchTerm unreadTerm = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
                combinedTerm = new AndTerm(dateRange, unreadTerm);
            }
            Message[] foundMessages = emailFolder.search(combinedTerm);
            log.info(
                    "Found {} total {} messages in date range via {}. Limiting to {}",
                    foundMessages.length,
                    unreadOnly ? "unread" : "matching",
                    protocol.value().toUpperCase(),
                    maxEmails
            );

            int count = 0;
            for (int i = foundMessages.length - 1; i >= 0 && count < maxEmails; i--) {
                folderMessages.add(foundMessages[i]);
                count++;
            }

            if (folderMessages.isEmpty()) {
                return List.of();
            }
            FetchProfile fetchProfile = new FetchProfile();
            fetchProfile.add(FetchProfile.Item.ENVELOPE);
            fetchProfile.add(FetchProfile.Item.CONTENT_INFO);
            emailFolder.fetch(folderMessages.toArray(new Message[0]), fetchProfile);
            // Jakarta Mail Message instances are invalid after the folder closes; snapshot fields now.
            return materializeToDtos(folderMessages);
        } finally {
            if (emailFolder != null && emailFolder.isOpen()) {
                try {
                    emailFolder.close(false);
                } catch (MessagingException e) {
                    log.warn("Failed to close email folder cleanly: {}", e.getMessage());
                }
            }
            if (store != null && store.isConnected()) {
                try {
                    store.close();
                } catch (MessagingException e) {
                    log.warn("Failed to close email store cleanly: {}", e.getMessage());
                }
            }
        }
    }

    private List<FetchedEmailDto> materializeToDtos(List<Message> connected) {
        List<FetchedEmailDto> out = new ArrayList<>(connected.size());
        for (Message message : connected) {
            try {
                out.add(toFetchedEmailDto(message));
            } catch (Exception e) {
                log.warn("Skipping email that could not be read while IMAP folder was open: {}", e.getMessage());
            }
        }
        return out;
    }

    private FetchedEmailDto toFetchedEmailDto(Message message) throws MessagingException, IOException {
        String emailId = getEmailId(message);
        String subject = message.getSubject();
        Date received = message.getReceivedDate();
        Instant receivedAt = received != null ? received.toInstant() : null;

        String inReplyTo = null;
        String references = null;
        if (message instanceof MimeMessage mimeMessage) {
            inReplyTo = Optional.ofNullable(mimeMessage.getHeader("In-Reply-To"))
                    .filter(h -> h.length > 0)
                    .map(h -> h[0])
                    .orElse(null);
            references = Optional.ofNullable(mimeMessage.getHeader("References"))
                    .filter(h -> h.length > 0)
                    .map(h -> h[0])
                    .orElse(null);
        }

        StringBuilder contentBuilder = new StringBuilder();
        List<AttachmentDto> attachments = new ArrayList<>();
        AtomicLong currentAttachmentSize = new AtomicLong(0);

        extractContentAndAttachments(message, contentBuilder, attachments, currentAttachmentSize);

        return FetchedEmailDto.builder()
                .emailId(emailId)
                .subject(subject)
                .senders(getAddresses(message.getFrom()))
                .recipientsTo(getAddresses(message.getRecipients(Message.RecipientType.TO)))
                .recipientsCc(getAddresses(message.getRecipients(Message.RecipientType.CC)))
                .content(contentBuilder.toString())
                .emailDate(resolveEmailDate(message))
                .receivedAt(receivedAt)
                .inReplyTo(inReplyTo)
                .references(references)
                .attachments(attachments)
                .build();
    }
    private List<String> getAddresses(Address[] addresses) {
        if (addresses == null) return Collections.emptyList();
        return Arrays.stream(addresses)
                .map(Address::toString)
                .toList();
    }
    private LocalDateTime resolveEmailDate(Message message) {
        try {
            Date sent = message.getSentDate();
            if (sent != null) {
                return sent.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            }
            Date received = message.getReceivedDate();
            if (received != null) {
                return received.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            }
        } catch (Exception e) {
            log.warn("Failed to extract message date: {}", e.getMessage());
        }
        return null;
    }

    private Properties buildMailProperties(AppSettings settings, String protocol, boolean useImplicitSsl) {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", protocol);
        properties.put("mail." + protocol + ".host", settings.getMailHost());
        properties.put("mail." + protocol + ".port", String.valueOf(settings.getMailPort()));
        properties.put("mail." + protocol + ".connectiontimeout", String.valueOf(imapConnectionTimeoutMs));
        properties.put("mail." + protocol + ".timeout", String.valueOf(imapReadTimeoutMs));
        properties.put("mail." + protocol + ".writetimeout", String.valueOf(imapWriteTimeoutMs));
        properties.put("mail." + protocol + ".ssl.trust", imapSslTrust);

        if (useImplicitSsl) {
            properties.put("mail." + protocol + ".ssl.enable", "true");
        } else {
            properties.put("mail.imap.starttls.enable", "true");
            properties.put("mail.imap.starttls.required", "true");
        }
        return properties;
    }

    private boolean shouldUseImplicitSsl(AppSettings settings) {
        return Boolean.TRUE.equals(settings.getMailSslEnabled())
                || settings.getMailPort() == null
                || settings.getMailPort() == DEFAULT_IMAPS_PORT;
    }

    private boolean isUnsupportedSslMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message != null) {
            String normalized = message.toLowerCase();
            if (normalized.contains("unsupported or unrecognized ssl message")
                    || normalized.contains("ssl handshake")
                    || normalized.contains("unable to find valid certification path")) {
                return true;
            }
        }
        return throwable.getCause() != null && isUnsupportedSslMessage(throwable.getCause());
    }

    private String getEmailId(Message message) throws MessagingException {
        if (message instanceof MimeMessage) {
            String messageId = ((MimeMessage) message).getMessageID();
            if (messageId != null && !messageId.isBlank()) {
                return messageId.trim();
            }
        }
        return "MSG-" + message.getMessageNumber();
    }

    /**
     * Recursively extracts content (text/plain, text/html) and attachments from a Part.
     * Implements memory management for attachments, skipping if total size exceeds MAX_TOTAL_ATTACHMENT_SIZE.
     *
     * @param part The Mime Part to process.
     * @param contentBuilder StringBuilder to append text content to.
     * @param attachments List to add AttachmentDto objects to.
     * @param currentAttachmentSize AtomicLong tracking the current total size of attachments.
     */
    private void extractContentAndAttachments(Part part, StringBuilder contentBuilder,
                                              List<AttachmentDto> attachments, AtomicLong currentAttachmentSize) throws MessagingException, IOException {
        if (part.isMimeType("text/plain")) {
            contentBuilder.append((String) part.getContent());
        } else if (part.isMimeType("text/html")) {
            // Jsoup could be used here for cleaning, but for simplicity, we'll append as is.
            contentBuilder.append((String) part.getContent());
        } else if (part.isMimeType("multipart/*")) {
            MimeMultipart mimeMultipart = (MimeMultipart) part.getContent();
            for (int i = 0; i < mimeMultipart.getCount(); i++) {
                BodyPart bodyPart = mimeMultipart.getBodyPart(i);
                extractContentAndAttachments(bodyPart, contentBuilder, attachments, currentAttachmentSize);
            }
        } else {
            // This part is likely an attachment or an inline image
            String disposition = part.getDisposition();
            String fileName = MimeUtility.decodeText(part.getFileName());

            // Check if it's an attachment or an inline file with a filename
            if (Part.ATTACHMENT.equalsIgnoreCase(disposition) ||
                    Part.INLINE.equalsIgnoreCase(disposition) ||
                    (fileName != null && !fileName.isBlank() && part.getSize() > 0)) {

                long attachmentSize = part.getSize(); // This might be -1 if size is unknown
                if (attachmentSize == -1) {
                    log.warn("Attachment '{}' has unknown size. Attempting to read to determine size.", fileName);
                    // If size is unknown, we'll read it and then check. This is less efficient.
                }

                if (currentAttachmentSize.get() + attachmentSize > MAX_TOTAL_ATTACHMENT_SIZE) {
                    log.warn("Skipping attachment '{}' (size: {} bytes) due to memory constraints. Total attachments would exceed {} MB.",
                            fileName, attachmentSize, MAX_TOTAL_ATTACHMENT_SIZE / (1024 * 1024));
                    return; // Skip this attachment
                }

                try (InputStream is = part.getInputStream();
                     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                    }
                    byte[] data = baos.toByteArray();
                    long actualSize = data.length;

                    // Double-check size after reading, if original size was -1 or estimate was off
                    if (currentAttachmentSize.get() + actualSize > MAX_TOTAL_ATTACHMENT_SIZE) {
                        log.warn("Skipping attachment '{}' (actual size: {} bytes) after reading due to memory constraints. Total attachments would exceed {} MB.",
                                fileName, actualSize, MAX_TOTAL_ATTACHMENT_SIZE / (1024 * 1024));
                        return;
                    }

                    attachments.add(AttachmentDto.builder()
                            .fileName(fileName)
                            .contentType(part.getContentType())
                            .data(data)
                            .size(actualSize)
                            .build());
                    currentAttachmentSize.addAndGet(actualSize);
                    log.debug("Added attachment '{}' of size {} bytes. Total attachments size: {} bytes.",
                            fileName, actualSize, currentAttachmentSize.get());

                } catch (IOException e) {
                    log.error("Failed to read attachment '{}': {}", fileName, e.getMessage());
                }
            }
        }
    }
}
