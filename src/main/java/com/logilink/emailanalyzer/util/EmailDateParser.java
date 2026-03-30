package com.logilink.emailanalyzer.util;

import org.apache.commons.lang3.StringUtils;

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

    /**
     * Parses LLM-provided date text into {@link LocalDateTime}, or {@code null} when not parseable.
     */
    public static LocalDateTime parseLlmEmailDate(String raw) {
        String s = StringUtils.trimToNull(raw);
        if (s == null) {
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
