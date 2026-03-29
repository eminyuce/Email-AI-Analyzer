package com.logilink.emailanalyzer.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Normalizes email/plain-text bodies before persistence: trims line noise and caps consecutive blank lines.
 */
public final class EmailContentNormalizer {

    /**
     * Maximum number of consecutive empty lines to keep (logical paragraph gaps).
     */
    private static final int MAX_CONSECUTIVE_BLANK_LINES = 3;

    private EmailContentNormalizer() {
    }

    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        if (input.isEmpty()) {
            return "";
        }

        String withUnixLines = input.replace("\r\n", "\n").replace("\r", "\n");
        String[] rawLines = withUnixLines.split("\n", -1);
        List<String> trimmedLines = new ArrayList<>(rawLines.length);
        for (String line : rawLines) {
            trimmedLines.add(line.trim());
        }

        List<String> collapsed = new ArrayList<>(trimmedLines.size());
        int blankRun = 0;
        for (String line : trimmedLines) {
            if (line.isEmpty()) {
                blankRun++;
                if (blankRun <= MAX_CONSECUTIVE_BLANK_LINES) {
                    collapsed.add("");
                }
            } else {
                blankRun = 0;
                collapsed.add(line);
            }
        }

        return trimOuterBlankLines(collapsed);
    }

    private static String trimOuterBlankLines(List<String> lines) {
        int i = 0;
        int j = lines.size() - 1;
        while (i <= j && lines.get(i).isEmpty()) {
            i++;
        }
        while (j >= i && lines.get(j).isEmpty()) {
            j--;
        }
        if (i > j) {
            return "";
        }
        return String.join("\n", lines.subList(i, j + 1));
    }
}
