package com.logilink.emailanalyzer.service;

import com.logilink.emailanalyzer.common.MailStoreProtocol;
import com.logilink.emailanalyzer.domain.AppSettings;
import com.logilink.emailanalyzer.exception.EmailAnalysisException;
import com.logilink.emailanalyzer.model.FetchedEmailDto;
import com.logilink.emailanalyzer.model.FetchedEmailDto.AttachmentDto;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import org.jsoup.Jsoup;
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
import java.util.function.Function;
import java.util.regex.Pattern;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final int DEFAULT_LOOKBACK_DAYS = 30;
    private static final int DEFAULT_IMAPS_PORT = 993;
    private static final long MAX_TOTAL_ATTACHMENT_SIZE = 200 * 1024 * 1024; // 200 MB
    /** Larger buffer = fewer read/write iterations for big attachments. */
    private static final int ATTACHMENT_COPY_BUFFER_SIZE = 64 * 1024;
    /**
     * Beyond this, building a full Jsoup DOM is often disproportionately slow; use a fast tag stripper instead.
     */
    private static final int HTML_JSOUP_MAX_CHARS = 350_000;
    private static final Pattern HTML_STRIP_SCRIPT = Pattern.compile("(?is)<script[^>]*>.*?</script>");
    private static final Pattern HTML_STRIP_STYLE = Pattern.compile("(?is)<style[^>]*>.*?</style>");
    private static final Pattern HTML_STRIP_TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern HTML_STRIP_WS = Pattern.compile("\\s+");

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
        log.debug(
                "EmailService constructed: imap connectionTimeoutMs={}, readTimeoutMs={}, writeTimeoutMs={}, ssl.trust={}",
                imapConnectionTimeoutMs,
                imapReadTimeoutMs,
                imapWriteTimeoutMs,
                imapSslTrust);
    }

    public List<FetchedEmailDto> fetchUnreadEmails(int maxEmails) {
        log.debug("fetchUnreadEmails: entry maxEmails={}, lookbackDays={}", maxEmails, DEFAULT_LOOKBACK_DAYS);
        if (maxEmails <= 0) {
            log.debug("fetchUnreadEmails: early exit (maxEmails<=0)");
            return List.of();
        }

        Instant now = Instant.now();
        Date endDate = Date.from(now);
        Date startDate = Date.from(now.minus(DEFAULT_LOOKBACK_DAYS, ChronoUnit.DAYS));
        log.debug("fetchUnreadEmails: derived range [{} .. {}]", startDate, endDate);
        List<FetchedEmailDto> out = fetchUnreadEmailsByRange(maxEmails, startDate, endDate);
        log.debug("fetchUnreadEmails: exit count={}", out.size());
        return out;
    }

    public List<FetchedEmailDto> fetchUnreadEmailsByRange(int maxEmails, Date startDate, Date endDate) {
        log.debug(
                "fetchUnreadEmailsByRange: delegating maxEmails={}, startDate={}, endDate={}",
                maxEmails,
                startDate,
                endDate);
        return fetchEmailsByRange(maxEmails, startDate, endDate, true, null);
    }

    public List<FetchedEmailDto> fetchEmails(int maxEmails) {
        return fetchEmails(maxEmails, null);
    }

    public List<FetchedEmailDto> fetchEmails(int maxEmails, Function<Collection<String>, Set<String>> existingEmailIdsLookup) {
        log.debug("fetchEmails: entry maxEmails={}, lookbackDays={}", maxEmails, DEFAULT_LOOKBACK_DAYS);
        if (maxEmails <= 0) {
            log.debug("fetchEmails: early exit (maxEmails<=0)");
            return List.of();
        }

        Instant now = Instant.now();
        Date endDate = Date.from(now);
        Date startDate = Date.from(now.minus(DEFAULT_LOOKBACK_DAYS, ChronoUnit.DAYS));
        log.debug("fetchEmails: derived range [{} .. {}]", startDate, endDate);
        List<FetchedEmailDto> out = fetchEmailsByRange(maxEmails, startDate, endDate, false, existingEmailIdsLookup);
        log.debug("fetchEmails: exit count={}", out.size());
        return out;
    }

    public List<FetchedEmailDto> fetchEmailsByRange(int maxEmails, Date startDate, Date endDate) {
        log.debug(
                "fetchEmailsByRange(3-arg): maxEmails={}, startDate={}, endDate={}, unreadOnly=false",
                maxEmails,
                startDate,
                endDate);
        return fetchEmailsByRange(maxEmails, startDate, endDate, false, null);
    }

    /**
     * Loads full message bodies only for emails whose IDs are not returned by {@code existingEmailIdsLookup}.
     */
    public List<FetchedEmailDto> fetchEmailsByRange(
            int maxEmails,
            Date startDate,
            Date endDate,
            Function<Collection<String>, Set<String>> existingEmailIdsLookup
    ) {
        return fetchEmailsByRange(maxEmails, startDate, endDate, false, existingEmailIdsLookup);
    }

    public List<FetchedEmailDto> fetchEmailsByRange(int maxEmails, Date startDate, Date endDate, boolean unreadOnly) {
        return fetchEmailsByRange(maxEmails, startDate, endDate, unreadOnly, null);
    }

    public List<FetchedEmailDto> fetchEmailsByRange(
            int maxEmails,
            Date startDate,
            Date endDate,
            boolean unreadOnly,
            Function<Collection<String>, Set<String>> existingEmailIdsLookup
    ) {
        log.debug(
                "fetchEmailsByRange: entry maxEmails={}, startDate={}, endDate={}, unreadOnly={}, skipKnownIds={}",
                maxEmails,
                startDate,
                endDate,
                unreadOnly,
                existingEmailIdsLookup != null);
        if (maxEmails <= 0) {
            log.debug("fetchEmailsByRange: early exit (maxEmails<=0)");
            return List.of();
        }
        if (startDate == null || endDate == null || startDate.after(endDate)) {
            log.debug(
                    "fetchEmailsByRange: invalid range startDate={} endDate={}",
                    startDate,
                    endDate);
            throw new EmailAnalysisException("Invalid date range for email search.");
        }

        log.debug("fetchEmailsByRange: loading required mail settings");
        AppSettings settings = appSettingsService.getRequiredMailSettings();
        List<FetchedEmailDto> emails = new ArrayList<>();

        boolean preferImplicitSsl = shouldUseImplicitSsl(settings);
        log.debug("fetchEmailsByRange: preferImplicitSsl={}", preferImplicitSsl);
        try {
            emails.addAll(fetchMessages(
                    settings, maxEmails, startDate, endDate, unreadOnly, preferImplicitSsl, existingEmailIdsLookup));
            log.debug(
                    "fetchEmailsByRange: first attempt OK — {} emails (unread filter: {})",
                    emails.size(),
                    unreadOnly ? "unread only" : "all");
        } catch (Exception firstException) {
            log.debug(
                    "fetchEmailsByRange: first attempt failed: {} — {}",
                    firstException.getClass().getSimpleName(),
                    firstException.getMessage());
            // Retry with STARTTLS when server rejects implicit SSL to avoid SSL handshake mismatch failures.
            if (preferImplicitSsl && isUnsupportedSslMessage(firstException)) {
                log.warn("IMAPS handshake failed; retrying mailbox fetch with IMAP STARTTLS.");
                emails.clear();
                try {
                    emails.addAll(fetchMessages(
                            settings, maxEmails, startDate, endDate, unreadOnly, false, existingEmailIdsLookup));
                    log.debug(
                            "fetchEmailsByRange: STARTTLS retry OK — {} emails (unread filter: {})",
                            emails.size(),
                            unreadOnly ? "unread only" : "all");
                } catch (MessagingException retryException) {
                    log.debug(
                            "fetchEmailsByRange: STARTTLS retry failed: {}",
                            retryException.getMessage(),
                            retryException);
                    throw new EmailAnalysisException("Failed to fetch emails within range", retryException);
                }
            } else {
                log.error("Error fetching emails: {}", firstException.getMessage());
                throw new EmailAnalysisException("Failed to fetch emails within range", firstException);
            }
        }
        log.debug("fetchEmailsByRange: exit total={}", emails.size());
        return emails;
    }

    private List<FetchedEmailDto> fetchMessages(
            AppSettings settings,
            int maxEmails,
            Date startDate,
            Date endDate,
            boolean unreadOnly,
            boolean useImplicitSsl,
            Function<Collection<String>, Set<String>> existingEmailIdsLookup
    ) throws MessagingException {

        MailStoreProtocol protocol = useImplicitSsl ? MailStoreProtocol.IMAPS : MailStoreProtocol.IMAP;
        log.debug(
                "fetchMessages start: protocol={}, implicitSsl={}, host={}, port={}, user={}, maxEmails={}, "
                        + "dateRange=[{} .. {}], unreadOnly={}",
                protocol.value(),
                useImplicitSsl,
                settings.getMailHost(),
                settings.getMailPort(),
                settings.getMailUsername(),
                maxEmails,
                startDate,
                endDate,
                unreadOnly);

        Properties properties = buildMailProperties(settings, protocol.value(), useImplicitSsl);
        Session emailSession = Session.getInstance(properties);
        log.debug("fetchMessages: Session created (debug disabled on session by default)");

        Store store = null;
        Folder emailFolder = null;

        try {
            log.debug("fetchMessages: getStore({})", protocol.value());
            store = emailSession.getStore(protocol.value());
            log.debug("fetchMessages: connecting store…");
            store.connect(settings.getMailHost(), settings.getMailPort(),
                    settings.getMailUsername(), settings.getMailPassword());
            log.debug("fetchMessages: store connected");

            emailFolder = store.getFolder("INBOX");
            log.debug("fetchMessages: opening INBOX READ_ONLY…");
            emailFolder.open(Folder.READ_ONLY);
            log.debug("fetchMessages: INBOX open, mode={}", emailFolder.getMode());

            // Secondary limit using message numbers to reduce load on large inboxes
            int totalMessages = emailFolder.getMessageCount();
            int messageNumberMultiplier = 5;           // You can tune this (higher = safer but more results)
            int messageNumberLimit = Math.max(200, maxEmails * messageNumberMultiplier);
            int fromMessageNumber = Math.max(1, totalMessages - messageNumberLimit + 1);
            log.debug(
                    "fetchMessages: inbox totalMessages={}, messageNumberLimit={}, fromMessageNumber={} (scan window)",
                    totalMessages,
                    messageNumberLimit,
                    fromMessageNumber);

            if (totalMessages == 0) {
                log.debug("fetchMessages: empty INBOX, returning no DTOs");
                return List.of();
            }

            // Avoid Folder.search(): on Gmail (and some other hosts) IMAP SEARCH with date terms can run
            // for a long time or appear busy when we already restrict by message sequence number.
            log.debug("fetchMessages: getMessages slice {}..{}", fromMessageNumber, totalMessages);
            Message[] slice = emailFolder.getMessages(fromMessageNumber, totalMessages);
            FetchProfile filterProfile = new FetchProfile();
            filterProfile.add(FetchProfile.Item.ENVELOPE);
            filterProfile.add(FetchProfile.Item.FLAGS);
            if (emailFolder instanceof UIDFolder) {
                filterProfile.add(UIDFolder.FetchProfileItem.UID);
            }
            long filterFetchStart = System.currentTimeMillis();
            emailFolder.fetch(slice, filterProfile);
            log.debug(
                    "fetchMessages: slice FETCH (ENVELOPE,FLAGS,UID) for msg# {}..{} count={} took {} ms",
                    fromMessageNumber,
                    totalMessages,
                    slice.length,
                    System.currentTimeMillis() - filterFetchStart);

            List<Message> matched = new ArrayList<>(slice.length);
            for (Message m : slice) {
                try {
                    if (matchesInboxFetchFilters(m, startDate, endDate, unreadOnly)) {
                        matched.add(m);
                    }
                } catch (MessagingException e) {
                    log.debug(
                            "fetchMessages: skip msgNum {} in client filter: {}",
                            m.getMessageNumber(),
                            e.getMessage());
                }
            }
            Message[] foundMessages = matched.toArray(Message[]::new);
            log.debug(
                    "fetchMessages: client-side filter → {} matches from {} slice messages (no server SEARCH)",
                    foundMessages.length,
                    slice.length);

            log.info("Found {} {} messages (msg# >= {}) via {}. Limiting to max {}",
                    foundMessages.length,
                    unreadOnly ? "unread" : "matching",
                    fromMessageNumber,
                    protocol.value().toUpperCase(),
                    maxEmails);

            if (foundMessages.length == 0) {
                return List.of();
            }

            // Sort by received date descending (newest first) and take only maxEmails
            Arrays.sort(foundMessages, (m1, m2) -> {
                try {
                    Date d1 = getMessageDate(m1);
                    Date d2 = getMessageDate(m2);
                    if (d1 == null && d2 == null) return 0;
                    if (d1 == null) return 1;
                    if (d2 == null) return -1;
                    return d2.compareTo(d1);
                } catch (Exception e) {
                    return 0;
                }
            });
            log.debug("fetchMessages: sorted {} messages by date (newest first)", foundMessages.length);

            List<Message> messagesToFetch = new ArrayList<>(Math.min(maxEmails, foundMessages.length));
            if (existingEmailIdsLookup != null) {
                List<String> candidateIds = new ArrayList<>(foundMessages.length);
                for (Message m : foundMessages) {
                    try {
                        candidateIds.add(getEmailId(m));
                    } catch (MessagingException e) {
                        log.debug(
                                "fetchMessages: synthetic id for msgNum {} (resolve id: {})",
                                m.getMessageNumber(),
                                e.getMessage());
                        candidateIds.add("MSG-" + m.getMessageNumber());
                    }
                }
                Set<String> alreadyProcessed = candidateIds.isEmpty()
                        ? Set.of()
                        : Optional.ofNullable(existingEmailIdsLookup.apply(candidateIds)).orElseGet(Set::of);
                int skippedKnown = 0;
                for (int i = 0; i < foundMessages.length && messagesToFetch.size() < maxEmails; i++) {
                    if (alreadyProcessed.contains(candidateIds.get(i))) {
                        skippedKnown++;
                        continue;
                    }
                    messagesToFetch.add(foundMessages[i]);
                }
                if (skippedKnown > 0) {
                    log.info(
                            "fetchMessages: skipped loading {} message body/bodies already present in database.",
                            skippedKnown);
                }
            } else {
                int limit = Math.min(maxEmails, foundMessages.length);
                messagesToFetch.addAll(Arrays.asList(Arrays.copyOfRange(foundMessages, 0, limit)));
            }

            if (log.isDebugEnabled()) {
                log.debug(
                        "fetchMessages: after sort+limit, will fetch {} messages (maxEmails={}, found={})",
                        messagesToFetch.size(),
                        maxEmails,
                        foundMessages.length);
                for (int j = 0; j < messagesToFetch.size(); j++) {
                    Message m = messagesToFetch.get(j);
                    try {
                        log.debug(
                                "fetchMessages: candidate [{}] msgNum={}, uid={}",
                                j + 1,
                                m.getMessageNumber(),
                                emailFolder instanceof UIDFolder uidFolder
                                        ? uidFolder.getUID(m)
                                        : "n/a");
                    } catch (MessagingException e) {
                        log.debug("fetchMessages: candidate [{}] msgNum=? (error: {})", j + 1, e.getMessage());
                    }
                }
            }

            // Optimized FetchProfile (BODYSTRUCTURE removed - it doesn't exist)
            FetchProfile fetchProfile = new FetchProfile();
            fetchProfile.add(FetchProfile.Item.ENVELOPE);
            fetchProfile.add(FetchProfile.Item.CONTENT_INFO);
            fetchProfile.add(FetchProfile.Item.FLAGS);
            fetchProfile.add(UIDFolder.FetchProfileItem.UID);

            log.debug(
                    "fetchMessages: batch FETCH profile items=ENVELOPE,CONTENT_INFO,FLAGS,UID count={}",
                    messagesToFetch.size());
            emailFolder.fetch(messagesToFetch.toArray(new Message[0]), fetchProfile);
            log.debug("fetchMessages: batch FETCH completed, calling materializeToDtos");

            List<FetchedEmailDto> dtos = materializeToDtos(messagesToFetch);
            log.debug("fetchMessages end: materialized {} DTOs", dtos.size());
            return dtos;

        } finally {
            log.debug("fetchMessages: finally — closing folder and store");
            if (emailFolder != null && emailFolder.isOpen()) {
                try {
                    log.debug("fetchMessages: closing INBOX (expunge=false)");
                    emailFolder.close(false);
                } catch (MessagingException e) {
                    log.warn("Failed to close email folder cleanly", e);
                }
            }
            if (store != null && store.isConnected()) {
                try {
                    log.debug("fetchMessages: closing store");
                    store.close();
                } catch (MessagingException e) {
                    log.warn("Failed to close email store cleanly", e);
                }
            }
            log.debug("fetchMessages: teardown complete");
        }
    }

    // Helper for safe date extraction during sorting
    private Date getMessageDate(Message msg) {
        try {
            Date received = msg.getReceivedDate();
            return (received != null) ? received : msg.getSentDate();
        } catch (MessagingException e) {
            return null;
        }
    }

    /**
     * Same criteria as the former server-side ANDterm (date window + optional unseen), applied to an
     * already-sequence-limited slice so we do not depend on {@link Folder#search}.
     */
    private boolean matchesInboxFetchFilters(Message m, Date startDate, Date endDate, boolean unreadOnly)
            throws MessagingException {
        Date d = getMessageDate(m);
        if (d == null) {
            return false;
        }
        if (d.before(startDate) || d.after(endDate)) {
            return false;
        }
        return !unreadOnly || !m.isSet(Flags.Flag.SEEN);
    }

    private List<FetchedEmailDto> materializeToDtos(List<Message> messages) {
        if (messages.isEmpty()) {
            log.debug("materializeToDtos: no messages, returning empty list");
            return List.of();
        }

        long startTime = System.currentTimeMillis();
        log.info("Materializing {} emails to DTOs sequentially...", messages.size());
        log.debug("materializeToDtos: begin batch size={}", messages.size());

        List<FetchedEmailDto> out = new ArrayList<>(messages.size());
        int successCount = 0;

        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            int msgNum = message.getMessageNumber();
            log.debug("materializeToDtos: [{}/{}] start msgNum={}", i + 1, messages.size(), msgNum);

            try {
                long msgStart = System.currentTimeMillis();

                FetchedEmailDto dto = toFetchedEmailDto(message);

                long duration = System.currentTimeMillis() - msgStart;
                out.add(dto);
                successCount++;

                int contentLen = dto.getContent() != null ? dto.getContent().length() : 0;
                int attCount = dto.getAttachments() != null ? dto.getAttachments().size() : 0;
                log.debug(
                        "materializeToDtos: [{}/{}] done msgNum={} emailId={} subject={} contentLen={} attachments={} {} ms",
                        i + 1,
                        messages.size(),
                        msgNum,
                        dto.getEmailId(),
                        getSafeSubject(dto),
                        contentLen,
                        attCount,
                        duration);

                // Smart logging - reduce noise
                if (duration > 400 || log.isDebugEnabled()) {
                    log.info("Processed email [{}/{}]: '{}' ({} ms)",
                            (i + 1), messages.size(), getSafeSubject(dto), duration);
                }

            } catch (Exception e) {
                log.warn("Failed to materialize email #{} - skipping",
                        message.getMessageNumber(), e);
                log.debug("materializeToDtos: [{}/{}] failed msgNum={}", i + 1, messages.size(), msgNum, e);
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Successfully materialized {}/{} emails in {} ms (avg: {} ms/email)",
                successCount, messages.size(), totalTime,
                messages.size() > 0 ? totalTime / messages.size() : 0);
        log.debug(
                "materializeToDtos: end success={}/{} totalMs={}",
                successCount,
                messages.size(),
                totalTime);

        return out;
    }

    // Safe subject for logging
    private String getSafeSubject(FetchedEmailDto dto) {
        return (dto.getSubject() != null && !dto.getSubject().isBlank())
                ? dto.getSubject()
                : "<no subject>";
    }
    private FetchedEmailDto toFetchedEmailDto(Message message) throws MessagingException, IOException {
        int msgNum = message.getMessageNumber();
        log.debug("toFetchedEmailDto: start msgNum={}", msgNum);

        String emailId = getEmailId(message);
        String subject = message.getSubject();
        Date received = message.getReceivedDate();
        Instant receivedAt = received != null ? received.toInstant() : null;
        log.debug(
                "toFetchedEmailDto: headers msgNum={} emailId={} subject={} receivedAt={}",
                msgNum,
                emailId,
                subject != null ? subject.substring(0, Math.min(60, subject.length())) : null,
                receivedAt);

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
            log.debug(
                    "toFetchedEmailDto: threading msgNum={} inReplyToPresent={} referencesPresent={}",
                    msgNum,
                    inReplyTo != null,
                    references != null);
        }

        StringBuilder contentBuilder = new StringBuilder();
        List<AttachmentDto> attachments = new ArrayList<>();
        AtomicLong currentAttachmentSize = new AtomicLong(0);

        log.debug("toFetchedEmailDto: extract body msgNum={} rootContentType={}", msgNum, message.getContentType());
        extractContentAndAttachments(message, contentBuilder, attachments, currentAttachmentSize);

        LocalDateTime emailDate = resolveEmailDate(message);
        List<String> from = getAddresses(message.getFrom());
        List<String> to = getAddresses(message.getRecipients(Message.RecipientType.TO));
        List<String> cc = getAddresses(message.getRecipients(Message.RecipientType.CC));
        log.debug(
                "toFetchedEmailDto: addresses msgNum={} fromCount={} toCount={} ccCount={}",
                msgNum,
                from.size(),
                to.size(),
                cc.size());

        FetchedEmailDto built = FetchedEmailDto.builder()
                .emailId(emailId)
                .subject(subject)
                .senders(from)
                .recipientsTo(to)
                .recipientsCc(cc)
                .content(contentBuilder.toString())
                .emailDate(emailDate)
                .receivedAt(receivedAt)
                .inReplyTo(inReplyTo)
                .references(references)
                .attachments(attachments)
                .build();
        log.debug(
                "toFetchedEmailDto: done msgNum={} contentChars={} attachmentCount={} totalAttachBytes={}",
                msgNum,
                contentBuilder.length(),
                attachments.size(),
                currentAttachmentSize.get());
        return built;
    }
    private List<String> getAddresses(Address[] addresses) {
        if (addresses == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(addresses)
                .map(Address::toString)
                .toList();
    }
    private LocalDateTime resolveEmailDate(Message message) {
        try {
            Date sent = message.getSentDate();
            if (sent != null) {
                LocalDateTime dt = sent.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                log.debug("resolveEmailDate: msgNum={} source=sent → {}", message.getMessageNumber(), dt);
                return dt;
            }
            Date received = message.getReceivedDate();
            if (received != null) {
                LocalDateTime dt = received.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                log.debug("resolveEmailDate: msgNum={} source=received → {}", message.getMessageNumber(), dt);
                return dt;
            }
        } catch (Exception e) {
            log.warn("Failed to extract message date: {}", e.getMessage());
        }
        log.debug("resolveEmailDate: msgNum={} → null", message.getMessageNumber());
        return null;
    }

    private Properties buildMailProperties(AppSettings settings, String protocol, boolean useImplicitSsl) {
        log.debug(
                "buildMailProperties: protocol={} useImplicitSsl={} host={} port={} timeouts conn/read/write={}/{}/{}",
                protocol,
                useImplicitSsl,
                settings.getMailHost(),
                settings.getMailPort(),
                imapConnectionTimeoutMs,
                imapReadTimeoutMs,
                imapWriteTimeoutMs);
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
            log.debug("buildMailProperties: SSL mode implicit (mail.{}.ssl.enable=true)", protocol);
        } else {
            properties.put("mail.imap.starttls.enable", "true");
            properties.put("mail.imap.starttls.required", "true");
            log.debug("buildMailProperties: STARTTLS required for IMAP");
        }
        return properties;
    }

    private boolean shouldUseImplicitSsl(AppSettings settings) {
        boolean sslFlag = Boolean.TRUE.equals(settings.getMailSslEnabled());
        Integer port = settings.getMailPort();
        boolean implicit = sslFlag || port == null || port == DEFAULT_IMAPS_PORT;
        log.debug(
                "shouldUseImplicitSsl: mailSslEnabled={} port={} imapsDefaultPort={} → useImplicitSsl={}",
                sslFlag,
                port,
                DEFAULT_IMAPS_PORT,
                implicit);
        return implicit;
    }

    private boolean isUnsupportedSslMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message != null) {
            String normalized = message.toLowerCase();
            if (normalized.contains("unsupported or unrecognized ssl message")
                    || normalized.contains("ssl handshake")
                    || normalized.contains("unable to find valid certification path")) {
                log.debug(
                        "isUnsupportedSslMessage: matched ssl-related text on {}",
                        throwable.getClass().getSimpleName());
                return true;
            }
        }
        if (throwable.getCause() != null) {
            boolean nested = isUnsupportedSslMessage(throwable.getCause());
            log.debug("isUnsupportedSslMessage: checking cause → {}", nested);
            return nested;
        }
        log.debug("isUnsupportedSslMessage: no match for {}", throwable.getClass().getSimpleName());
        return false;
    }

    private String getEmailId(Message message) throws MessagingException {
        if (message instanceof MimeMessage mimeMessage) {
            String messageId = mimeMessage.getMessageID();
            if (messageId != null && !messageId.isBlank()) {
                String id = messageId.trim();
                log.debug("getEmailId: Message-ID header msgNum={}", message.getMessageNumber());
                return id;
            }
        }
        String synthetic = "MSG-" + message.getMessageNumber();
        log.debug("getEmailId: fallback synthetic id={}", synthetic);
        return synthetic;
    }

    private static long elapsedMsSince(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000L;
    }

    private static ByteArrayOutputStream newAttachmentBuffer(long reportedSizeBytes) {
        if (reportedSizeBytes > 0 && reportedSizeBytes <= 50 * 1024 * 1024) {
            return new ByteArrayOutputStream((int) reportedSizeBytes);
        }
        return new ByteArrayOutputStream(16_384);
    }

    /**
     * Jsoup is accurate but slow on very large HTML; fast strip preserves throughput for newsletter-style messages.
     */
    private String htmlEmailToPlainText(String html) {
        if (html.length() <= HTML_JSOUP_MAX_CHARS) {
            return Jsoup.parse(html).text();
        }
        if (log.isDebugEnabled()) {
            log.debug(
                    "htmlEmailToPlainText: fast strip (chars={} > jsoupCap={})",
                    html.length(),
                    HTML_JSOUP_MAX_CHARS);
        }
        String s = HTML_STRIP_SCRIPT.matcher(html).replaceAll(" ");
        s = HTML_STRIP_STYLE.matcher(s).replaceAll(" ");
        s = HTML_STRIP_TAGS.matcher(s).replaceAll(" ");
        return HTML_STRIP_WS.matcher(s).replaceAll(" ").trim();
    }

    /**
     * Inline/attached images are ignored so newsletters and HTML signatures do not bloat attachment lists.
     */
    private boolean isSkippableImagePart(Part part, String fileName) throws MessagingException {
        if (part.isMimeType("image/*")) {
            return true;
        }
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String n = fileName.toLowerCase(Locale.ROOT);
        return n.endsWith(".png")
                || n.endsWith(".jpg")
                || n.endsWith(".jpeg")
                || n.endsWith(".gif")
                || n.endsWith(".webp")
                || n.endsWith(".bmp")
                || n.endsWith(".svg")
                || n.endsWith(".ico")
                || n.endsWith(".tif")
                || n.endsWith(".tiff")
                || n.endsWith(".heic")
                || n.endsWith(".avif");
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
        extractContentAndAttachments(part, contentBuilder, attachments, currentAttachmentSize, 0);
    }

    private void extractContentAndAttachments(Part part, StringBuilder contentBuilder,
                                              List<AttachmentDto> attachments, AtomicLong currentAttachmentSize,
                                              int depth) throws MessagingException, IOException {

        long tPartStart = System.nanoTime();
        String branch = "unknown";
        String contentType = part.getContentType();

        if (log.isDebugEnabled()) {
            log.debug(
                    "extractContentAndAttachments: depth={} enter contentType={} disposition={} fileName={} size={}",
                    depth,
                    contentType,
                    part.getDisposition(),
                    part.getFileName(),
                    part.getSize());
        }

        try {
            if (part.isMimeType("text/plain")) {
                branch = "text/plain";
                long t0 = System.nanoTime();
                String text = (String) part.getContent();
                long getContentMs = elapsedMsSince(t0);
                contentBuilder.append(text);
                if (log.isDebugEnabled()) {
                    log.debug(
                            "extractContentAndAttachments: PART_TIMING detail depth={} branch={} getContentMs={} plainChars={}",
                            depth,
                            branch,
                            getContentMs,
                            text.length());
                }
            } else if (part.isMimeType("text/html")) {
                branch = "text/html";
                long t0 = System.nanoTime();
                String html = (String) part.getContent();
                long getContentMs = elapsedMsSince(t0);
                long t1 = System.nanoTime();
                String text = htmlEmailToPlainText(html);
                long toPlainMs = elapsedMsSince(t1);
                contentBuilder.append(text);
                if (log.isDebugEnabled()) {
                    log.debug(
                            "extractContentAndAttachments: depth={} text/html rawChars={} strippedChars={}",
                            depth,
                            html.length(),
                            text.length());
                    log.debug(
                            "extractContentAndAttachments: PART_TIMING detail depth={} branch={} getContentMs={} htmlToPlainMs={}",
                            depth,
                            branch,
                            getContentMs,
                            toPlainMs);
                }
            } else if (part.isMimeType("multipart/*")) {
                branch = "multipart/*";
                long t0 = System.nanoTime();
                MimeMultipart mimeMultipart = (MimeMultipart) part.getContent();
                long getContentMs = elapsedMsSince(t0);
                int count = mimeMultipart.getCount();
                if (log.isDebugEnabled()) {
                    log.debug(
                            "extractContentAndAttachments: depth={} multipart parts={} getContentMs={}",
                            depth,
                            count,
                            getContentMs);
                }
                long tChildren = System.nanoTime();
                for (int i = 0; i < count; i++) {
                    BodyPart bodyPart = mimeMultipart.getBodyPart(i);
                    extractContentAndAttachments(bodyPart, contentBuilder, attachments, currentAttachmentSize, depth + 1);
                }
                if (log.isDebugEnabled()) {
                    log.debug(
                            "extractContentAndAttachments: PART_TIMING detail depth={} branch={} recurseChildrenMs={} childCount={}",
                            depth,
                            branch,
                            elapsedMsSince(tChildren),
                            count);
                }
            } else {
                branch = "leaf_other";
                String disposition = part.getDisposition();
                String rawName = part.getFileName();
                String fileName = rawName != null ? MimeUtility.decodeText(rawName) : null;

                if (Part.ATTACHMENT.equalsIgnoreCase(disposition) ||
                        Part.INLINE.equalsIgnoreCase(disposition) ||
                        (fileName != null && !fileName.isBlank() && part.getSize() > 0)) {

                    if (isSkippableImagePart(part, fileName)) {
                        branch = "skip_image";
                        if (log.isDebugEnabled()) {
                            log.debug(
                                    "extractContentAndAttachments: depth={} skip image fileName={} contentType={}",
                                    depth,
                                    fileName,
                                    contentType);
                        }
                        return;
                    }

                    branch = "attachment_read";
                    if (log.isDebugEnabled()) {
                        log.debug(
                                "extractContentAndAttachments: depth={} attachment candidate fileName={}",
                                depth,
                                fileName);
                    }
                    long attachmentSize = part.getSize();
                    if (attachmentSize == -1) {
                        log.warn("Attachment '{}' has unknown size. Attempting to read to determine size.", fileName);
                    }

                    if (currentAttachmentSize.get() + attachmentSize > MAX_TOTAL_ATTACHMENT_SIZE) {
                        branch = "attachment_skip_cap_estimate";
                        if (log.isDebugEnabled()) {
                            log.debug(
                                    "extractContentAndAttachments: depth={} skip '{}' — would exceed cap (estimated)",
                                    depth,
                                    fileName);
                        }
                        log.warn("Skipping attachment '{}' (size: {} bytes) due to memory constraints. Total attachments would exceed {} MB.",
                                fileName, attachmentSize, MAX_TOTAL_ATTACHMENT_SIZE / (1024 * 1024));
                        return;
                    }

                    try (InputStream is = part.getInputStream();
                         ByteArrayOutputStream baos = newAttachmentBuffer(attachmentSize)) {
                        long tRead = System.nanoTime();
                        byte[] buffer = new byte[ATTACHMENT_COPY_BUFFER_SIZE];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            baos.write(buffer, 0, bytesRead);
                        }
                        long readMs = elapsedMsSince(tRead);
                        byte[] data = baos.toByteArray();
                        long actualSize = data.length;

                        if (log.isDebugEnabled()) {
                            log.debug(
                                    "extractContentAndAttachments: PART_TIMING detail depth={} branch={} streamReadMs={} bytes={}",
                                    depth,
                                    branch,
                                    readMs,
                                    actualSize);
                        }

                        if (currentAttachmentSize.get() + actualSize > MAX_TOTAL_ATTACHMENT_SIZE) {
                            branch = "attachment_skip_cap_after_read";
                            if (log.isDebugEnabled()) {
                                log.debug(
                                        "extractContentAndAttachments: depth={} skip '{}' after read — exceeds cap",
                                        depth,
                                        fileName);
                            }
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
                        if (log.isDebugEnabled()) {
                            log.debug(
                                    "Added attachment '{}' size={} bytes cumulativeAttachBytes={}",
                                    fileName,
                                    actualSize,
                                    currentAttachmentSize.get());
                        }

                    } catch (IOException e) {
                        branch = "attachment_io_error";
                        if (log.isDebugEnabled()) {
                            log.debug(
                                    "extractContentAndAttachments: depth={} IOException on '{}': {}",
                                    depth,
                                    fileName,
                                    e.getMessage());
                        }
                        log.error("Failed to read attachment '{}': {}", fileName, e.getMessage());
                    }
                } else {
                    branch = "ignored_leaf";
                    if (log.isDebugEnabled()) {
                        log.debug(
                                "extractContentAndAttachments: depth={} leaf not treated as attachment mime={}",
                                depth,
                                contentType);
                    }
                }
            }
        } finally {
            if (log.isDebugEnabled()) {
                log.debug(
                        "extractContentAndAttachments: PART_TIMING depth={} branch={} totalMs={}",
                        depth,
                        branch,
                        elapsedMsSince(tPartStart));
            }
        }
    }
}
