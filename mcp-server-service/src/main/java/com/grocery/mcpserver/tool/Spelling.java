package com.grocery.mcpserver.tool;

/**
 * Whether a word the customer typed is the same word with a spelling mistake: "tomoto" and "tomatto" are "tomato",
 * "poteto" is "potato", "bananna" is "banana", "orgnic" is "organic".
 *
 * The rule is the one of the website's search suggestions (Elasticsearch's "AUTO" fuzziness, product-read-service), so
 * the shop and the chat agree on what a small typo is:
 * - the first letter must be right (people rarely get it wrong, and it keeps "milk" from becoming "silk");
 * - a word of 3 to 5 letters may have 1 letter wrong, a longer word 2; a shorter word must be exact.
 * A letter wrong means one letter added, left out or replaced (the Levenshtein distance).
 */
final class Spelling {

    private Spelling() {
    }

    /**
     * @param said a word as the customer typed it, in lower case ("tomoto")
     * @param word a word of a product's name, in lower case ("tomato")
     */
    static boolean isClose(String said, String word) {
        if (said.equals(word)) {
            return true;
        }
        if (said.isEmpty() || word.isEmpty() || said.charAt(0) != word.charAt(0)) {
            return false;
        }
        int allowed = said.length() < 3 ? 0 : said.length() <= 5 ? 1 : 2;
        return allowed > 0 && Math.abs(said.length() - word.length()) <= allowed && distance(said, word) <= allowed;
    }

    /** How many letters must be added, left out or replaced to turn one word into the other: "tomoto" -> "tomato" is 1 */
    static int distance(String one, String other) {
        int[] previous = new int[other.length() + 1];
        int[] current = new int[other.length() + 1];
        for (int j = 0; j <= other.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= one.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= other.length(); j++) {
                int replace = previous[j - 1] + (one.charAt(i - 1) == other.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(replace, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[other.length()];
    }
}
