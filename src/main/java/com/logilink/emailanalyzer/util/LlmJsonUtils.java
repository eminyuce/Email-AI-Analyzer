package com.logilink.emailanalyzer.util;

import org.apache.commons.lang3.StringUtils;

/**
 * Helpers for extracting JSON payloads from LLM responses that may include markdown fences or prose.
 */
public final class LlmJsonUtils {

    private LlmJsonUtils() {
    }

    /**
     * Returns the substring from the first {@code '{'} through the last {@code '}'} when present;
     * otherwise strips common markdown fences and trims.
     */
    public static String cleanJson(String input) {
        if (StringUtils.isBlank(input)) {
            return "{}";
        }

        int start = input.indexOf('{');
        int end = input.lastIndexOf('}');

        if (start != -1 && end != -1 && end > start) {
            return input.substring(start, end + 1);
        }

        return input.replace("```json", "").replace("```", "").trim();
    }

    /**
     * Removes leading {@code ```} fences (optionally with a {@code json} language tag) from model
     * output before Jackson parsing.
     */
    public static String unwrapModelJsonPayload(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            int fence = t.lastIndexOf("```");
            if (firstNl >= 0 && fence > firstNl) {
                t = t.substring(firstNl + 1, fence).trim();
                if (t.regionMatches(true, 0, "json", 0, 4)) {
                    t = t.substring(4).trim();
                }
            }
        }
        return t;
    }
}
