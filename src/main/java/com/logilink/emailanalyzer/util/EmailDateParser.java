package com.logilink.emailanalyzer.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Best-effort parsing for dates returned by LLMs in structured JSON (often non-ISO or mixed formats).
 */
public final class EmailDateParser {

    private static final DateTimeFormatter GMAIL_DISPLAY =
            DateTimeFormatter.ofPattern("EEE, MMM d, yyyy 'at' h:mm a", Locale.US);

    private EmailDateParser() {
    }

    public static LocalDateTime parseLlmEmailDate(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(s).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception ignored) {
            // continue
        }
        try {
            return OffsetDateTime.parse(s).toLocalDateTime();
        } catch (Exception ignored) {
            // continue
        }
        try {
            return ZonedDateTime.parse(s).toLocalDateTime();
        } catch (Exception ignored) {
            // continue
        }
        try {
            return LocalDateTime.parse(s);
        } catch (Exception ignored) {
            // continue
        }
        try {
            return LocalDateTime.parse(s, GMAIL_DISPLAY);
        } catch (Exception ignored) {
            // continue
        }
        return null;
    }
}
