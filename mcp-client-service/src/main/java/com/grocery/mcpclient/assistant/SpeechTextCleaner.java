package com.grocery.mcpclient.assistant;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes an answer easy to listen to before it is read aloud. speaches already leaves out emoji and *markdown*
 * emphasis; this writes out what the voice would read badly (measured: "₹58.00" was read as "rupee 58.00", and
 * "1 kg, Rs. 28" as "1K-G-R's 28"):
 *
 *   ₹58.00, Rs. 58, INR 58     -> 58 rupees          ₹58.50 -> 58 rupees 50 paise
 *   100 g, 1 kg, 500 ml, 2 L   -> 100 grams, 1 kilogram, 500 millilitres, 2 litres
 *   https://...                -> left out (nobody can follow a spoken link)
 *   list lines ("- Amul ...")  -> sentences
 */
public final class SpeechTextCleaner {

    private static final Pattern MONEY = Pattern.compile("(?:₹|\\bRs\\.?|\\bINR)\\s*(\\d[\\d,]*)(?:\\.(\\d{1,2}))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern UNIT = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\s?(kg|kgs|g|gm|gms|ml|l|ltr|litre|liter)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Map<String, String[]> UNIT_WORDS = Map.of(          // {one, many}
            "kg", new String[] {"kilogram", "kilograms"}, "kgs", new String[] {"kilogram", "kilograms"},
            "g", new String[] {"gram", "grams"}, "gm", new String[] {"gram", "grams"}, "gms", new String[] {"gram", "grams"},
            "ml", new String[] {"millilitre", "millilitres"},
            "l", new String[] {"litre", "litres"}, "ltr", new String[] {"litre", "litres"},
            "litre", new String[] {"litre", "litres"}, "liter", new String[] {"litre", "litres"});
    private static final Pattern LINK = Pattern.compile("https?://\\S+");
    private static final Pattern LIST_MARK = Pattern.compile("(?m)^\\s*(?:[-*•]|\\d+[.)])\\s+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private SpeechTextCleaner() {
    }

    public static String clean(String answer) {
        if (answer == null) {
            return "";
        }
        String text = LINK.matcher(answer).replaceAll("");
        text = replace(MONEY, text, SpeechTextCleaner::spokenMoney);
        text = replace(UNIT, text, SpeechTextCleaner::spokenUnit);
        text = sentencesFromListLines(text);
        return SPACES.matcher(text).replaceAll(" ").strip();
    }

    private static String spokenMoney(Matcher money) {
        String rupees = money.group(1).replace(",", "");
        String paise = money.group(2);
        String spoken = rupees + (rupees.equals("1") ? " rupee" : " rupees");
        if (paise != null && Integer.parseInt(paise) > 0) {
            int paiseValue = paise.length() == 1 ? Integer.parseInt(paise) * 10 : Integer.parseInt(paise);   // .5 = 50 paise
            spoken += " " + paiseValue + " paise";
        }
        return spoken;
    }

    private static String spokenUnit(Matcher unit) {
        String amount = unit.group(1);
        String[] words = UNIT_WORDS.get(unit.group(2).toLowerCase(Locale.ROOT));
        return amount + " " + (amount.equals("1") ? words[0] : words[1]);
    }

    /** "- Amul Butter, 58 rupees" on its own line becomes "Amul Butter, 58 rupees." so the voice pauses after it. */
    private static String sentencesFromListLines(String text) {
        if (!LIST_MARK.matcher(text).find()) {
            return text;
        }
        StringBuilder spoken = new StringBuilder();
        for (String line : text.split("\\R")) {
            String sentence = LIST_MARK.matcher(line).replaceFirst("").strip();
            if (sentence.isEmpty()) {
                continue;
            }
            spoken.append(sentence);
            if (!sentence.matches(".*[.!?:]$")) {
                spoken.append('.');
            }
            spoken.append(' ');
        }
        return spoken.toString();
    }

    private static String replace(Pattern pattern, String text, Function<Matcher, String> spoken) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(spoken.apply(matcher)));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
