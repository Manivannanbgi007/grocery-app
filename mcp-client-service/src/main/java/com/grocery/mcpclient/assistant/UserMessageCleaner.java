package com.grocery.mcpclient.assistant;

import com.grocery.mcpclient.exception.BusinessException;

import java.util.regex.Pattern;

/**
 * Cleans the customer's message before the model sees it (the length is already checked by @Size).
 *
 * - Removes invisible characters (control characters, zero-width and text-direction characters): they are never
 *   needed in a shopping question, and are a known way to hide instructions from people reading the chat.
 * - Collapses long runs of spaces and empty lines.
 */
public final class UserMessageCleaner {

    /** Control (Cc) and format (Cf) characters, except tab, newline and carriage return. */
    private static final Pattern INVISIBLE = Pattern.compile("[\\p{Cc}\\p{Cf}&&[^\\t\\n\\r]]");
    private static final Pattern SPACES = Pattern.compile("[ \\t]+");
    private static final Pattern EMPTY_LINES = Pattern.compile("(\\r?\\n\\s*){3,}");

    private UserMessageCleaner() {
    }

    /**
     * @return the cleaned message
     * @throws BusinessException when nothing readable is left
     */
    public static String clean(String message) {
        String cleaned = INVISIBLE.matcher(message).replaceAll("");
        cleaned = SPACES.matcher(cleaned).replaceAll(" ");
        cleaned = EMPTY_LINES.matcher(cleaned).replaceAll("\n\n");
        cleaned = cleaned.strip();
        if (cleaned.isEmpty()) {
            throw new BusinessException("Please type a message");
        }
        return cleaned;
    }
}
