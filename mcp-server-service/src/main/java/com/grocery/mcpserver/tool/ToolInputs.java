package com.grocery.mcpserver.tool;

import java.util.Locale;

/**
 * Small checks for tool arguments. The model fills them in, so they can be anything: empty, very long,
 * full of control characters. Each tool checks its arguments and answers INVALID_INPUT with a clear message.
 */
final class ToolInputs {

    /** The longest search text or name a tool accepts. */
    static final int MAX_TEXT_LENGTH = 100;

    private ToolInputs() {
    }

    /** The text without control characters and outer spaces, or null when nothing is left. */
    static String clean(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.replaceAll("\\p{Cntrl}", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** At most MAX_TEXT_LENGTH characters, with at least one letter or digit. */
    static boolean isUsableText(String text) {
        return text.length() <= MAX_TEXT_LENGTH && text.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    /**
     * True when a name the model gave matches a real name, loosely: "fruit" matches "Fruits",
     * "fresh mart store" matches "Fresh Mart". Case does not matter.
     */
    static boolean looselyMatches(String given, String actual) {
        String a = given.toLowerCase(Locale.ROOT);
        String b = actual.toLowerCase(Locale.ROOT);
        return a.equals(b) || b.contains(a) || a.contains(b);
    }
}
