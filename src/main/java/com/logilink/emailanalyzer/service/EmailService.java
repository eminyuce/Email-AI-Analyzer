package com.logilink.emailanalyzer.service;

import com.logilink.emailanalyzer.common.MailStoreProtocol;
import com.logilink.emailanalyzer.domain.AppSettings;
import com.logilink.emailanalyzer.exception.EmailAnalysisException;
import com.logilink.emailanalyzer.model.FetchedEmailDto;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.SearchTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final int DEFAULT_LOOKBACK_DAYS = 30;
    private static final int DEFAULT_IMAPS_PORT = 993;

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
        String sender = "";
        if (message.getFrom() != null && message.getFrom().length > 0 && message.getFrom()[0] != null) {
            sender = message.getFrom()[0].toString();
        }
        String content = getTextFromMessage(message);
        Date received = message.getReceivedDate();
        Instant receivedAt = received != null ? received.toInstant() : null;
        return FetchedEmailDto.builder()
            .emailId(emailId)
            .subject(subject)
            .sender(sender)
            .content(content != null ? content : "")
            .emailDate(resolveEmailDate(message))
            .receivedAt(receivedAt)
            .build();
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

    private String getTextFromMessage(Message message) throws MessagingException, IOException {
        String result = "";
        if (message.isMimeType("text/plain")) {
            result = message.getContent().toString();
        } else if (message.isMimeType("multipart/*")) {
            MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
            result = getTextFromMimeMultipart(mimeMultipart);
        }
        return result;
    }

    private String getTextFromMimeMultipart(MimeMultipart mimeMultipart) throws MessagingException, IOException {
        StringBuilder result = new StringBuilder();
        int count = mimeMultipart.getCount();
        for (int i = 0; i < count; i++) {
            BodyPart bodyPart = mimeMultipart.getBodyPart(i);
            if (bodyPart.isMimeType("text/plain")) {
                result.append("\n").append(bodyPart.getContent());
                break; // preferred
            } else if (bodyPart.isMimeType("text/html")) {
                String html = (String) bodyPart.getContent();
                result.append("\n").append(html); // Simplistic, should really strip HTML tags
            } else if (bodyPart.getContent() instanceof MimeMultipart) {
                result.append(getTextFromMimeMultipart((MimeMultipart) bodyPart.getContent()));
            }
        }
        return result.toString();
    }
}
