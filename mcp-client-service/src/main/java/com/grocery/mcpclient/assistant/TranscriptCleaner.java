package com.grocery.mcpclient.assistant;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Cleans the words Whisper heard in a recording before they become the user's question.
 *
 * - Whisper sometimes "hears" a few words in silence or noise: "you", "Thank you.", "Thanks for watching!" (measured:
 *   3 s of silence gave "you"). speaches already leaves out silence (vad_filter), this catches what gets through.
 *   Such a text alone is no question, so it becomes "" and the caller answers "couldn't hear a question".
 * - Removes sound notes like "[music]" or "(laughs)", invisible characters and extra spaces.
 * - Starts the question with a capital letter ("which offers ..." -> "Which offers ...").
 */
public final class TranscriptCleaner {

    /** What Whisper invents from silence, compared in lower case without punctuation. */
    private static final Set<String> NOT_A_QUESTION = Set.of("you", "thank you", "thanks", "thank you very much",
            "thanks for watching", "thank you for watching", "bye", "bye bye");

    private static final Pattern SOUND_NOTES = Pattern.compile("\\[[^]]*]|\\([^)]*\\)");
    private static final Pattern INVISIBLE = Pattern.compile("[\\p{Cc}\\p{Cf}]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern NOT_A_WORD_CHARACTER = Pattern.compile("[^\\p{L}\\p{N} ]");

    private TranscriptCleaner() {
    }

    /** @return the question, or "" when the recording held no real words */
    public static String clean(String heard) {
        if (heard == null) {
            return "";
        }
        String text = SOUND_NOTES.matcher(heard).replaceAll(" ");
        text = INVISIBLE.matcher(text).replaceAll(" ");
        text = SPACES.matcher(text).replaceAll(" ").strip();

        String words = NOT_A_WORD_CHARACTER.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("").strip();
        if (words.codePoints().filter(Character::isLetter).count() < 2 || NOT_A_QUESTION.contains(words)) {
            return "";
        }
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
