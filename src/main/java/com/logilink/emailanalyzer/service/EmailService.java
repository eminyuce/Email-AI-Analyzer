package com.logilink.emailanalyzer.service;

import com.logilink.emailanalyzer.domain.AppSettings;
import com.logilink.emailanalyzer.exception.EmailAnalysisException;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.search.FlagTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final AppSettingsService appSettingsService;

    public EmailService(AppSettingsService appSettingsService) {
        this.appSettingsService = appSettingsService;
    }

    public List<Message> fetchUnreadEmails() {
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

            Message[] messages = emailFolder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
            for (Message message : messages) {
                unreadMessages.add(message);
            }

        } catch (Exception e) {
            log.error("Error fetching emails: {}", e.getMessage());
            throw new EmailAnalysisException("Failed to fetch emails", e);
        }
        return unreadMessages;
    }

    public String getEmailId(Message message) throws MessagingException {
        if (message instanceof MimeMessage) {
            return ((MimeMessage) message).getMessageID();
        }
        return String.valueOf(message.getMessageNumber());
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
