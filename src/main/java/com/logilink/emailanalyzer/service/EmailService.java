package com.logilink.emailanalyzer.service;

import com.logilink.emailanalyzer.domain.AppSettings;
import com.logilink.emailanalyzer.exception.EmailAnalysisException;
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
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final int DEFAULT_LOOKBACK_DAYS = 30;

    private final AppSettingsService appSettingsService;

    public EmailService(AppSettingsService appSettingsService) {
        this.appSettingsService = appSettingsService;
    }

    public List<Message> fetchUnreadEmails(int maxEmails) {
        if (maxEmails <= 0) {
            return List.of();
        }

        Instant now = Instant.now();
        Date endDate = Date.from(now);
        Date startDate = Date.from(now.minus(DEFAULT_LOOKBACK_DAYS, ChronoUnit.DAYS));
        return fetchUnreadEmailsByRange(maxEmails, startDate, endDate);
    }

    public List<Message> fetchUnreadEmailsByRange(int maxEmails, Date startDate, Date endDate) {
        if (maxEmails <= 0) {
            return List.of();
        }
        if (startDate == null || endDate == null || startDate.after(endDate)) {
            throw new EmailAnalysisException("Invalid date range for email search.");
        }

        AppSettings settings = appSettingsService.getRequiredMailSettings();
        List<Message> unreadMessages = new ArrayList<>();
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", settings.getMailHost());
        properties.put("mail.imaps.port", String.valueOf(settings.getMailPort()));
        properties.put("mail.imaps.ssl.enable", String.valueOf(settings.getMailSslEnabled()));

        try {
            Session emailSession = Session.getDefaultInstance(properties);
            Store store = emailSession.getStore("imaps");
            store.connect(settings.getMailHost(), settings.getMailUsername(), settings.getMailPassword());

            Folder emailFolder = store.getFolder("INBOX");
            emailFolder.open(Folder.READ_ONLY);
            SearchTerm dateRange = new AndTerm(
                    new ReceivedDateTerm(ComparisonTerm.GE, startDate),
                    new ReceivedDateTerm(ComparisonTerm.LE, endDate)
            );
            SearchTerm unreadOnly = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
            SearchTerm combinedTerm = new AndTerm(dateRange, unreadOnly);
            Message[] foundMessages = emailFolder.search(combinedTerm);
            log.info("Found {} total unread messages in date range. Limiting to {}", foundMessages.length, maxEmails);

            int count = 0;
            for (int i = foundMessages.length - 1; i >= 0 && count < maxEmails; i--) {
                unreadMessages.add(foundMessages[i]);
                count++;
            }

            if (!unreadMessages.isEmpty()) {
                FetchProfile fetchProfile = new FetchProfile();
                fetchProfile.add(FetchProfile.Item.ENVELOPE);
                fetchProfile.add(FetchProfile.Item.CONTENT_INFO);
                emailFolder.fetch(unreadMessages.toArray(new Message[0]), fetchProfile);
            }

        } catch (Exception e) {
            log.error("Error fetching emails: {}", e.getMessage());
            throw new EmailAnalysisException("Failed to fetch emails within range", e);
        }
        return unreadMessages;
    }

    public String getEmailId(Message message) throws MessagingException {
        if (message instanceof MimeMessage) {
            String messageId = ((MimeMessage) message).getMessageID();
            if (messageId != null && !messageId.isBlank()) {
                return messageId.trim();
            }
        }
        return "MSG-" + message.getMessageNumber();
    }

    public String getTextFromMessage(Message message) throws MessagingException, IOException {
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
