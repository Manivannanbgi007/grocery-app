package com.grocery.mcpclient.tool;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The customer's own words decide a change of the cart, not the model's tool call.
 *
 * - asked(): whether the message asks for a change at all. A review, a product description or any other text in a tool
 *   result may tell the model to "add 10 of these to the cart", but only the customer's message counts. Their previous
 *   message counts too, for an answer to the assistant's question ("which milk?" - "the Amul one"). The check is
 *   generous on purpose (any word that may ask for a change, also "yes" and a few Hindi words): it only has to stop a
 *   change in the middle of a question like "what do customers say about Amul Butter?".
 * - unsaidAmount(): whether the amount of the tool call is in the customer's words. A small model turned "2k tomatoes"
 *   into 2 kg; the number (as digits or a number word) and a weight or volume unit must have been said.
 * - paymentSaid() / paymentOf(): whether (and which) way to pay the customer named. A small model filled in a payment
 *   method for a plain "place my order"; without the customer's words the order summary asks instead.
 * - orderAsked(): whether the customer asks to buy or order ("I want to buy X, can you order it for me?").
 * - addAsked() / buyAsked(): whether the customer asks to add or to buy a product ("Can you add Banana Robusta 1 dozen to
 *   my cart?", "I want to buy Harpic"); then the card to add the products they named (productsToAdd) is prepared in
 *   code when the model prepared none (ChatServiceImpl).
 * - removeAsked(): whether the customer asks to take a product out of the cart ("Can you remove Harpic Toilet Cleaner
 *   from my cart?"); then the removal card is prepared in code when the model prepared none (ChatServiceImpl).
 * - clearAsked(): whether the customer asks to empty the whole cart ("empty my cart"); then the same for that card.
 * - productSaid() / sameProduct() / wordsAbout() / listedProducts() / productsToAdd(): several products in one message
 *   ("Add Tomato 1 kg, Onion 2 kg and Potato 3 kg"): which product the customer named, which part of their words says
 *   its amount, and the products they ask for with their amounts (for the code's own step when the model did not
 *   prepare them).
 * - isAmountReply() / amountReply() / cancelAsked(): the customer's answer to "How much Tomato would you like to add?"
 *   ("3kg", "Three kilograms", "I need 3 kilos", or "never mind"), which ChatServiceImpl handles in code.
 */
public final class ChangeRequest {

    private static final List<String> WORDS = List.of(
            "add", "adding", "put", "buy", "order", "want", "need", "get", "give", "remove", "delete", "drop", "take out",
            "change", "set", "make", "increase", "decrease", "reduce", "update", "replace", "more", "less", "instead",
            "yes", "yeah", "ok", "okay", "sure",
            // Hindi, typed in Latin letters and in Devanagari: "do tamatar dalo", "doodh chahiye", "pyaaz hatao"
            "chahiye", "dalo", "daalo", "daal do", "hatao", "hata do", "nikalo", "badlo", "karo", "kar do", "le lo",
            "चाहिए", "डालो", "डाल दो", "हटाओ", "हटा दो", "निकालो", "बदलो", "करो", "कर दो", "ले लो");
    /** (?iU): any case, and word boundaries that also work for Devanagari */
    private static final Pattern CHANGE_WORDS = anyOf(WORDS);

    /** Weight and volume units, each with the ways a customer writes it ("2 kg", "500g", "two kilos") */
    private static final List<List<String>> UNITS = List.of(
            List.of("kg", "kgs", "kilo", "kilos", "kilogram", "kilograms", "किलो"),
            List.of("g", "gm", "gms", "gram", "grams", "ग्राम"),
            List.of("l", "ltr", "litre", "litres", "liter", "liters", "लीटर"),
            List.of("ml", "millilitre", "millilitres", "milliliter", "milliliters", "मिलीलीटर"));
    /** Numbers written as words (English, Hindi in Latin letters and in Devanagari) */
    private static final Map<String, List<String>> NUMBER_WORDS = Map.ofEntries(
            Map.entry("0.5", List.of("half", "aadha", "adha", "आधा")),
            Map.entry("1.5", List.of("one and a half", "dedh", "डेढ़")),
            Map.entry("2", List.of("two", "couple", "do", "दो")),
            Map.entry("2.5", List.of("two and a half", "dhai", "ढाई")),
            Map.entry("3", List.of("three", "teen", "तीन")),
            Map.entry("4", List.of("four", "char", "chaar", "चार")),
            Map.entry("5", List.of("five", "paanch", "panch", "पांच", "पाँच")),
            Map.entry("6", List.of("six", "chhe", "chhah", "छह")),
            Map.entry("7", List.of("seven", "saat", "सात")),
            Map.entry("8", List.of("eight", "aath", "आठ")),
            Map.entry("9", List.of("nine", "nau", "नौ")),
            Map.entry("10", List.of("ten", "das", "दस")),
            Map.entry("11", List.of("eleven")), Map.entry("12", List.of("twelve")), Map.entry("13", List.of("thirteen")),
            Map.entry("14", List.of("fourteen")), Map.entry("15", List.of("fifteen")), Map.entry("16", List.of("sixteen")),
            Map.entry("17", List.of("seventeen")), Map.entry("18", List.of("eighteen")), Map.entry("19", List.of("nineteen")),
            Map.entry("20", List.of("twenty")));
    /**
     * "do" is Hindi for two ("do kilo tamatar"), but also the English verb. In "i do not want tomato in my cart. can you
     * remove it please" (the user's words, 2026-10-30), "do you have milk?" or "please do" it names no amount, so these
     * uses are taken out before the number words are looked for.
     */
    private static final Pattern ENGLISH_DO = Pattern.compile("(?iU)\\b(?:i|we|you|they|please)\\s+do\\b"
            + "|\\bdo(?=\\s+(?:not|you|i|we|they|it|this|that|so|the|a|an|any|my|your)\\b)");
    private static final String DEVANAGARI_DIGITS = "०१२३४५६७८९";
    /** Cash on delivery, as customers name it: "COD", "cash payment", "pay when it is delivered", "pay after delivery" */
    private static final Pattern CASH_WORDS = Pattern.compile(anyOf(List.of("cash", "cod", "cash on delivery", "nakad", "naqad",
            "नकद", "कैश")).pattern() + "|\\b(?:pay|paying|payment)\\b[^.?!]{0,25}\\b(?:on|after|at|upon|when)\\b[^.?!]{0,25}"
            + "\\b(?:deliver(?:y|ed|s)?|arrives?|door)\\b");
    /** Paying online, as customers name it */
    private static final Pattern ONLINE_WORDS = anyOf(List.of("online", "upi", "card", "credit card", "debit card",
            "net banking", "netbanking", "prepaid", "gpay", "google pay", "phonepe", "paytm", "razorpay", "ऑनलाइन"));
    /**
     * Words that ask to pay for (or buy) the whole cart now, besides PLACE_ORDER_WORDS: "proceed to pay", "pay online",
     * "pay for my cart", "let's complete the payment", "buy everything in my cart", "I want to buy them by online payment"
     * (and its typo "by all of them"). Patterns, no fixed sentences: a "go ahead" verb with a payment word, "pay" with
     * what or how, or "buy" with the cart.
     */
    private static final Pattern PAY_NOW_WORDS = Pattern.compile("(?iU)"
            + "\\b(?:proceed|continue|go\\s+ahead|complete|finish|make|do)\\b[^.?!]{0,30}\\b(?:pay|payment|checkout|check\\s?out|purchase)\\b"
            + "|\\bpay(?:ing)?\\b[^.?!]{0,30}\\b(?:it|them|this|cart|order|everything|all|items|bill|now|online|by|via|using|with"
            + "|cash|cod|upi|card|after|when)\\b"
            + "|\\b(?:buy|by|purchase|order)\\b[^.?!]{0,40}\\b(?:my|the)\\s+cart\\b"
            + "|\\b(?:buy|purchase)\\s+(?:them|everything|all)\\b|\\bby\\s+(?:all|everything)\\b");
    /** "How can I pay?", "Which payment methods do you take?", "Do you accept UPI?": a question about paying, no request */
    private static final Pattern PAYMENT_QUESTION = Pattern.compile("(?iU)^\\s*(?:how|which|what|do\\s+you|does)\\b");
    /** "Don't place the order", "I do not want to pay now": the opposite of a request to order */
    private static final Pattern NOT_CHECKOUT = Pattern.compile("(?iU)\\b(?:don[’']?t|do\\s+not|never)\\b[^.?!]{0,20}"
            + "\\b(?:place|pay|order|buy|purchase|checkout|proceed)\\b");
    /** The words of a request to pay or order the cart, with the ways to pay: "proceed to pay via online", "COD please" */
    private static final Set<String> CHECKOUT_WORDS = Set.of("proceed", "continue", "go", "ahead", "complete", "finish", "make",
            "pay", "paying", "payment", "payments", "checkout", "check", "out", "place", "order", "purchase", "buy", "by",
            "online", "upi", "card", "net", "banking", "netbanking", "prepaid", "razorpay", "gpay", "phonepe", "paytm", "cash",
            "cod", "nakad", "naqad", "delivery", "deliver", "delivered", "door", "arrives", "on", "after", "when", "upon", "at",
            "via", "using", "with", "through", "prefer", "rather", "instead", "method", "mode", "them", "all", "everything",
            "whole", "entire", "which", "is", "are", "it", "this", "that", "now", "then", "let", "lets", "s", "ok", "okay", "yes",
            "sure", "no", "not");
    /** Words of a way to pay, never part of a product's name: "buy Harpic by online payment" is Harpic */
    private static final Set<String> PAYMENT_WORDS = Set.of("online", "payment", "payments", "pay", "cash", "cod", "upi", "via",
            "using", "by", "them", "everything", "which");
    /**
     * Words that ask to order the CART now: "place my order", "checkout", "order my cart", "order karo". Not every
     * "order": "where is my order?" and "cancel my order" are no request to order, and "order it for me" is about a
     * product (it goes into the cart first, see orderAsked()).
     */
    private static final Pattern PLACE_ORDER_WORDS = Pattern.compile("(?iU)\\bplace\\s+(?:(?:my|the|an|this|our)\\s+)?order\\b"
            + "|\\bcheck\\s?out\\b|\\border\\s+(?:my|the)\\s+cart\\b|\\border\\s+(?:karo|kardo|kar\\s+do)\\b");
    /** How unsaidAmount()'s question starts */
    private static final String UNSAID_AMOUNT = "The amount \"";
    /** Words that ask to add to the cart: "add", "put", "one more", "another", "dalo" */
    private static final Pattern ADD_WORDS = anyOf(List.of("add", "adding", "put", "one more", "another", "include",
            "dalo", "daalo", "daal do", "डालो", "डाल दो"));
    /** Words that ask to buy a product: "I want to buy X", "purchase", "kharido" (not "order": "where is my order?") */
    private static final List<String> BUY = List.of("buy", "purchase", "kharid", "kharido", "kharidna", "खरीद", "खरीदो", "खरीदना");
    private static final Pattern BUY_WORDS = anyOf(BUY);
    /** Words that ask to buy or order: "I want to buy X", "can you order it for me?", "place my order" */
    private static final Pattern ORDER_WORDS = anyOf(Stream.concat(BUY.stream(),
            Stream.of("order", "checkout", "check out", "ऑर्डर")).toList());
    /** Where a sentence ends: "I want to buy this Harpic product. Can you order it for me?" (not the dot of "1.5 kg") */
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.?!])\\s+");
    /** Words that ask to take a product out of the cart: "remove", "delete", "take the X out", "hatao", "nikalo" */
    private static final Pattern REMOVE_WORDS = Pattern.compile(anyOf(List.of("remove", "delete", "drop", "take out",
            "take off", "get rid of", "hatao", "hata do", "nikalo", "nikal do", "हटाओ", "हटा दो", "निकालो", "निकाल दो")).pattern()
            + "|\\btake\\s+(?:\\S+\\s+){1,5}out\\b");
    /** "Don't remove the tomatoes", "please do not delete it": the opposite of a request to remove */
    private static final Pattern NOT_REMOVE = Pattern.compile("(?iU)\\b(?:don[’']?t|do\\s+not|never)\\s+(?:remove|delete|drop|take)\\b");
    /**
     * Words that ask to empty the whole cart: "empty my cart", "clear the cart", "remove everything from my cart",
     * "delete all items from the cart", "cart khali karo". Not "remove all the tomatoes": that is one product.
     */
    private static final Pattern CLEAR_WORDS = Pattern.compile("(?iU)\\b(?:empty|clear)\\s+(?:out\\s+)?(?:my|the|this)\\s+(?:cart|basket)\\b"
            + "|\\b(?:remove|delete|take\\s+out)\\s+(?:everything|all(?:\\s+(?:the\\s+)?(?:items|products|things))?)"
            + "\\s+(?:from|in|of)\\s+(?:my|the|this)\\s+(?:cart|basket)\\b"
            + "|\\b(?:cart|basket)\\s+(?:ko\\s+)?(?:khali|khaali)\\s+(?:karo|kar\\s+do)\\b|कार्ट\\s+खाली");
    /** "Empty my cart except the milk": then only some products go, which the model handles */
    private static final Pattern EXCEPT_WORDS = anyOf(List.of("except", "but not", "apart from", "other than", "keep", "leave"));
    /** One, as customers say it: "remove 1 Harpic" or "remove one Harpic" may mean one pack less, not the whole line */
    private static final Pattern ONE = anyOf(List.of("1", "one", "ek", "एक"));
    /** One pack, as customers ask for it: "add a tomato", "one more", "another one", "a single pack" */
    private static final Pattern ONE_SAID = anyOf(List.of("1", "one", "a", "an", "another", "single", "ek", "एक"));
    /** Where one product of a list ends: "Tomato 1 kg, Onion 2 kg and Potato 3 kg", "tamatar aur pyaaz" */
    private static final Pattern LIST_BREAK = Pattern.compile("(?iU)[,;\\n]|&|\\b(?:and|aur)\\b|और");
    /** The units prepareMyCartAdd knows, as customers write them (see toolUnit) */
    private static final String UNIT = "(kg|kgs|kilos?|kilograms?|g|gms?|grams?|l|ltrs?|litres?|liters?|ml|packs?|packets?"
            + "|bottles?|dozens?|pcs?|pieces?)";
    /** A number with its unit, for one product of a list: "2 kg", "1L", "500 g", "2 packets", "1 dozen", "6 pcs" */
    private static final Pattern AMOUNT_WITH_UNIT = Pattern.compile("(?iU)(\\d+(?:[.,]\\d+)?)\\s*" + UNIT + "\\b");
    /** A number with or without its unit, for the answer to "how much?": "3 kg", "3kg", "2 of kilos", "3" (packs) */
    private static final Pattern AMOUNT = Pattern.compile("(?iU)(\\d+(?:[.,]\\d+)?)(?:\\s*(?:of\\s+)?" + UNIT + "\\b)?");
    /** "a kilo", "an extra pack": one of that unit */
    private static final Pattern A_UNIT = Pattern.compile("(?iU)\\b(?:a|an)\\s+(?=" + UNIT + "\\b)");
    /** "-2 kg": no amount to add */
    private static final Pattern NEGATIVE = Pattern.compile("-\\s*\\d");
    /** Words of an answer to "how much?" around its amount: "yes, make it 3 kg", "ok, about 2 packs", "2 or 3 kg" */
    private static final Set<String> REPLY_WORDS = Set.of("yes", "yeah", "yep", "ok", "okay", "sure", "only", "make", "about",
            "around", "total", "with", "let", "lets", "s", "dozens", "or", "and", "anymore");
    /**
     * Words that call off what the assistant asked: "cancel", "no", "never mind", "forget it", "don't add it", "rehne do".
     * (An answer with an amount is an amount: "no, 3 kg".)
     */
    private static final Pattern CANCEL_WORDS = Pattern.compile(anyOf(List.of("cancel", "no", "nope", "never mind",
            "nevermind", "forget it", "forget about it", "stop", "skip", "leave it", "not now", "nothing", "nahi", "rehne do",
            "नहीं", "रहने दो")).pattern() + "|\\bdon[’']?t\\b|\\bdo\\s+not\\b");
    /** The words of a request around the products of a list: "can you add ... in my cart?", "I want ... please" */
    private static final Set<String> REQUEST_WORDS = Set.of("can", "could", "would", "will", "you", "please", "pls", "plz",
            "kindly", "add", "put", "include", "buy", "order", "get", "want", "need", "i", "me", "for", "to", "into", "in",
            "my", "the", "cart", "basket", "a", "an", "one", "some", "also", "too", "of", "this", "these", "now", "just",
            "more", "it", "them", "that", "those", "dalo", "daalo", "chahiye", "mujhe",
            // more of the same, for productsToAdd: "Can you add one more item for X", "I would like to buy this X product"
            "item", "items", "product", "products", "like", "another", "extra", "purchase", "we", "us", "our", "hi", "hello",
            "hey", "thanks", "thank", "is", "are", "do", "does", "be", "go", "ahead", "here", "there", "kar", "karo");
    /** Words that are no part of a product's name: "the", "some", and the units and packs of an amount */
    private static final Set<String> NOT_A_NAME = Set.of("a", "an", "the", "of", "some", "my", "more", "please", "x",
            "kg", "kgs", "kilo", "kilos", "kilogram", "kilograms", "g", "gm", "gms", "gram", "grams", "l", "ltr", "litre",
            "litres", "liter", "liters", "ml", "pack", "packs", "packet", "packets", "piece", "pieces", "pc", "pcs", "dozen",
            "bottle", "bottles");

    private ChangeRequest() {
    }

    /**
     * @param message         the customer's message
     * @param previousMessage their message before it in this conversation; null when there is none
     */
    public static boolean asked(String message, String previousMessage) {
        return asks(message) || asks(previousMessage);
    }

    /**
     * @param customerWords the customer's message and the one before it
     * @param quantity      the tool call's "quantity" (a number; null when the model left it out)
     * @param unit          the tool call's "unit" (null when the model left it out)
     * @return null when the amount is in the customer's words (or the tool call has none); otherwise the question to
     *         ask the customer instead of changing the cart
     */
    public static String unsaidAmount(String customerWords, Object quantity, Object unit) {
        BigDecimal number = numberOf(quantity);
        String unitWord = unit == null ? "" : unit.toString().strip().toLowerCase(Locale.ROOT).replaceAll("\\.$", "");
        String words = withAsciiDigits(customerWords);
        boolean numberSaid = number == null || isSaid(number, words);
        boolean unitSaid = unitWord.isEmpty() || isSaid(unitWord, words);
        if (numberSaid && unitSaid) {
            return null;
        }
        String amount = ((number == null ? "" : number.toPlainString()) + " " + unitWord).strip();
        return UNSAID_AMOUNT + amount + "\" is not in the customer's words. Ask the customer how much they want (for "
                + "example 2 kg or 2 packs) and change nothing until they answer.";
    }

    /**
     * Whether a tool result is unsaidAmount()'s question: that cart call changed nothing. ("Can you add one more item
     * for X?": the model set X to 2, which is right, but "2" is not in the words; the code then adds one pack itself.)
     */
    public static boolean isUnsaidAmountQuestion(String message) {
        return message != null && message.startsWith(UNSAID_AMOUNT) && message.contains("is not in the customer's words");
    }

    /** @param customerWords the customer's message and the one before it */
    public static boolean paymentSaid(String customerWords) {
        return paymentOf(customerWords) != null;
    }

    /**
     * @param customerWords the customer's message and the one before it
     * @return "cash" or "online" (prepareMyOrder's words) when the customer named a way to pay, else null
     */
    public static String paymentOf(String customerWords) {
        if (customerWords == null) {
            return null;
        }
        if (CASH_WORDS.matcher(customerWords).find()) {
            return "cash";
        }
        return ONLINE_WORDS.matcher(customerWords).find() ? "online" : null;
    }

    /** @param customerWords the customer's message and the one before it: do they ask to buy or to order? */
    public static boolean orderAsked(String customerWords) {
        return customerWords != null && ORDER_WORDS.matcher(customerWords).find();
    }

    /** @param message the customer's message: does it ask to add to the cart ("can you add one more item for X")? */
    public static boolean addAsked(String message) {
        return message != null && ADD_WORDS.matcher(message).find();
    }

    /** @param message the customer's message: does it ask to buy a product ("I want to buy X", "kharidna hai")? */
    public static boolean buyAsked(String message) {
        return message != null && BUY_WORDS.matcher(message).find();
    }

    /**
     * @param message the customer's message: does it ask to take a product out of the cart completely ("Can you remove
     *                Harpic Toilet Cleaner from my cart?", "pyaaz hatao")? Not when it names an amount, also not one
     *                ("remove 1 Harpic", "remove 2 kg tomatoes"): that may mean less of it, which the model handles.
     *                Not "don't remove the tomatoes" either.
     */
    public static boolean removeAsked(String message) {
        if (message == null || !REMOVE_WORDS.matcher(message).find() || NOT_REMOVE.matcher(message).find()) {
            return false;
        }
        // the amount is looked for in the other words: the "do" of "hata do" ("remove it") is no "do" (two)
        String otherWords = REMOVE_WORDS.matcher(message).replaceAll(" ");
        return !namesAnAmount(otherWords) && !ONE.matcher(withAsciiDigits(otherWords)).find();
    }

    /**
     * @param message the customer's message: does it ask to empty the whole cart ("Empty my cart", "remove everything from
     *                my cart")? Not when it keeps something ("... except the milk").
     */
    public static boolean clearAsked(String message) {
        return message != null && CLEAR_WORDS.matcher(message).find() && !EXCEPT_WORDS.matcher(message).find();
    }

    /**
     * @param message the customer's message
     * @return its words before "keep", "except", "but not" ...: "Remove Tomato and Potato from my cart, but keep Organic
     *         Carrot" -> "Remove Tomato and Potato from my cart, but "; the whole message when it keeps nothing
     */
    public static String withoutKeptProducts(String message) {
        if (message == null) {
            return "";
        }
        Matcher kept = EXCEPT_WORDS.matcher(message);
        return kept.find() ? message.substring(0, kept.start()) : message;
    }

    /**
     * @param words the customer's words: do they name an amount other than one ("2 kg", "three", "500 g", "12 eggs")?
     *              Then only the model's tool call can carry it (with the amount checks); "one more", "a", "an" are one.
     */
    public static boolean namesAnAmount(String words) {
        if (words == null) {
            return false;
        }
        String text = withoutEnglishDo(withAsciiDigits(words));
        Matcher numbers = Pattern.compile("\\d+(?:[.,]\\d+)?").matcher(text);
        while (numbers.find()) {
            if (!"1".equals(numbers.group())) {
                return true;
            }
        }
        return UNITS.stream().flatMap(List::stream).anyMatch(spelling -> spellingSaid(spelling, text))
                || NUMBER_WORDS.values().stream().anyMatch(spellings -> anyOf(spellings).matcher(text).find());
    }

    /** "kg" in "2 kg", "2kg" or "a kilo" */
    private static boolean spellingSaid(String spelling, String text) {
        return Pattern.compile("(?iU)(?:\\b|(?<=\\d))" + Pattern.quote(spelling) + "\\b").matcher(text).find();
    }

    /** @param message the customer's message: does it ask to order the cart now ("Place my order", "checkout")? */
    public static boolean placeOrderAsked(String message) {
        return message != null && PLACE_ORDER_WORDS.matcher(message).find();
    }

    /**
     * @param message the customer's message: does it ask to order or pay for the cart now? "Place my order", "checkout",
     *                "Proceed to pay", "Pay online", "I want to purchase everything in my cart using online payment",
     *                "Pay when the order is delivered". Not a question about paying ("How can I pay?"), not "where is my
     *                order?" and not "don't place the order".
     */
    public static boolean checkoutAsked(String message) {
        if (message == null || NOT_CHECKOUT.matcher(message).find()) {
            return false;
        }
        return placeOrderAsked(message) || PAY_NOW_WORDS.matcher(message).find() && !PAYMENT_QUESTION.matcher(message).find();
    }

    /**
     * Whether every word of the message is a word of paying or ordering the cart (or of a request): "Proceed to pay via
     * online", "Online.", "COD please", "i want to buy them by online payment. can you proceed payment". Not when it also
     * names something else: "proceed to pay for the tomatoes", "add 2 kg onions and checkout".
     */
    public static boolean onlyAboutCheckout(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return Arrays.stream(message.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{M}]+"))
                .filter(word -> !word.isEmpty())
                .allMatch(word -> CHECKOUT_WORDS.contains(word) || isNoName(word) || REPLY_WORDS.contains(word));
    }

    /**
     * Whether the message only calls off the checkout: "cancel", "no", "never mind", "don't place the order". Not "cancel
     * my last order" (an order that exists: prepareMyOrderCancellation) and not "no, pay online".
     */
    public static boolean checkoutCancelAsked(String message) {
        if (message == null || !CANCEL_WORDS.matcher(message).find() || paymentOf(message) != null || checkoutAsked(message)) {
            return false;
        }
        String rest = CANCEL_WORDS.matcher(message).replaceAll(" ");
        return rest.replaceAll("[^\\p{L}\\p{M}]+", "").isEmpty() || onlyAboutCheckout(rest);
    }

    /**
     * Whether the message says only an amount, as the answer to "How much Tomato would you like to add?": "3kg", "3 kg",
     * "Three kilograms", "I need 3 kilos", "Just 3 kg, please", "yes, 3 packs of tomatoes", "0 kg". Not "3 kg onions"
     * (another product: a new request) and not "what does it cost?" (no amount).
     *
     * @param product the product of the question ("Tomato"): the customer may name it again
     */
    public static boolean isAmountReply(String message, String product) {
        String text = withNumbers(message);
        return text.matches("(?s).*\\d.*") && onlyRequestWords(text, product);
    }

    /** Whether every word (not counting numbers) is a word of a request, an answer or the product's name */
    private static boolean onlyRequestWords(String text, String product) {
        Set<String> productWords = productWords(product);
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{M}]+"))
                .filter(word -> !word.isEmpty())
                .allMatch(word -> isNoName(word) || REPLY_WORDS.contains(word) || productWords.contains(singular(word)));
    }

    /**
     * The one amount of an answer to "How much Tomato would you like to add?": "3kg" and "Three kilograms" -> (Tomato, 3,
     * kg), "2 packs" and a bare "2" -> (Tomato, 2, packs). Null when the answer has no amount, two ("2 or 3 kg"), or one
     * that is not above zero ("0 kg", "-2 kg"): then the question is asked again.
     *
     * @param product the product of the question, for the result
     */
    public static ListedProduct amountReply(String message, String product) {
        String text = withNumbers(message);
        if (NEGATIVE.matcher(text).find()) {
            return null;
        }
        Matcher amount = AMOUNT.matcher(text);
        if (!amount.find()) {
            return null;
        }
        BigDecimal quantity = new BigDecimal(amount.group(1).replace(',', '.'));
        String unit = amount.group(2) == null ? "packs" : toolUnit(amount.group(2));
        if (amount.find() || quantity.signum() <= 0) {
            return null;
        }
        return new ListedProduct(product, quantity, unit);
    }

    /**
     * Whether the message only calls off the question "How much Tomato would you like to add?": "cancel", "never mind",
     * "No, don't add it", "no thanks". Not "I don't need tomato, add 2 kg onions instead": that is a new request.
     *
     * @param product the product of the question ("Tomato"): the customer may name it again
     */
    public static boolean cancelAsked(String message, String product) {
        if (message == null || !CANCEL_WORDS.matcher(message).find()) {
            return false;
        }
        return onlyRequestWords(CANCEL_WORDS.matcher(message).replaceAll(" "), product);
    }

    /**
     * The message with its numbers as digits: "three kilograms" -> "3 kilograms", "one pack" -> "1 pack", "half a kilo"
     * -> "0.5 kilo", "a dozen" -> "1 dozen", "दो किलो" -> "2 किलो"
     */
    private static String withNumbers(String message) {
        String text = withoutEnglishDo(withAsciiDigits(message)).replaceAll("(?iU)\\bhalf\\s+an?\\b", "half");
        // the longest spellings first: "two and a half" before "two"
        List<Map.Entry<String, String>> spellings = NUMBER_WORDS.entrySet().stream()
                .flatMap(number -> number.getValue().stream().map(spelling -> Map.entry(spelling, number.getKey())))
                .sorted(Comparator.comparingInt((Map.Entry<String, String> spelling) -> spelling.getKey().length()).reversed())
                .toList();
        for (Map.Entry<String, String> spelling : spellings) {
            text = anyOf(List.of(spelling.getKey())).matcher(text).replaceAll(spelling.getValue());
        }
        return A_UNIT.matcher(ONE.matcher(text).replaceAll("1")).replaceAll("1 ");
    }

    /**
     * @param customerWords the customer's message and the one before it
     * @param product       a cart tool call's "product"
     * @return whether the customer named that product: each of its words is in their words, singular or plural alike
     *         ("tomato" in "add 1 kg tomatoes"). "Amul Taaza Toned Milk" is not in "add milk and 1 kg tomatoes": the
     *         model took it from a list of choices.
     */
    public static boolean productSaid(String customerWords, Object product) {
        Set<String> productWords = productWords(product);
        return !productWords.isEmpty() && productWords(customerWords).containsAll(productWords);
    }

    /**
     * Whether two names mean the same product: all words of one are in the other ("milk" and "Amul Taaza Toned Milk",
     * "tomatoes" and "Tomato"). "Akshayakalpa Organic Milk" and "Organic Carrot" share a word, but are two products.
     */
    public static boolean sameProduct(Object product, Object other) {
        Set<String> words = productWords(product);
        Set<String> otherWords = productWords(other);
        return !words.isEmpty() && !otherWords.isEmpty() && (words.containsAll(otherWords) || otherWords.containsAll(words));
    }

    /**
     * A product of the customer's list and the amount they said for it: "Organic Carrot 2 kg" -> ("Organic Carrot", 2,
     * "kg"). The unit is one prepareMyCartAdd knows: kg, g, l, ml, packs, dozen or pieces. No quantity and no unit: the
     * customer said no amount (the add tool then asks how much); a quantity without a unit: packs.
     */
    public record ListedProduct(String product, BigDecimal quantity, String unit) {
    }

    /**
     * The products a message lists with an amount for each, one per part (split at commas and "and"): "can you add
     * Akshayakalpa Organic Milk 1L and Organic Carrot 2 kg in my cart?" -> (Akshayakalpa Organic Milk, 1, l) and (Organic
     * Carrot, 2, kg). The words of the request ("can you add", "in my cart") are no product.
     * Only what is exact counts: a part with one number and its unit ("2 kg", "1L", "500 g", "2 packets", "1 dozen") and
     * the product's name. A part without an amount is left out ("order them for me", "then place my order", or a
     * product without an amount: the model handles those). The list is empty when a part has an unclear amount ("2k",
     * "two kilos", "6 eggs") or an amount without a product: such an amount is never read in code.
     */
    public static List<ListedProduct> listedProducts(String message) {
        if (message == null) {
            return List.of();
        }
        List<ListedProduct> products = new ArrayList<>();
        for (String part : LIST_BREAK.split(message)) {
            Matcher amount = AMOUNT_WITH_UNIT.matcher(part);
            if (!amount.find()) {
                if (namesAnAmount(part)) {
                    return List.of();   // an amount that is not a number with its unit: "2k", "6 eggs", "two kilos"
                }
                continue;               // no amount: not for the code ("order them for me")
            }
            String rest = part.substring(0, amount.start()) + " " + part.substring(amount.end());
            if (namesAnAmount(rest)) {
                return List.of();       // a second amount in the same part: "2 kg 500 g tomatoes"
            }
            // the words of the product: no request words, and no "1" ("add 1 Amul Butter" is one pack of it)
            String product = Arrays.stream(rest.split("[^\\p{L}\\p{M}\\p{N}]+"))
                    .filter(word -> !word.isEmpty() && !word.matches("\\d+") && !REQUEST_WORDS.contains(word.toLowerCase(Locale.ROOT)))
                    .collect(Collectors.joining(" "));
            if (product.isEmpty()) {
                return List.of();       // an amount without a product: "add 2 kg of it"
            }
            products.add(new ListedProduct(product, new BigDecimal(amount.group(1).replace(',', '.')), toolUnit(amount.group(2))));
        }
        return products;
    }

    /**
     * The products the message asks to add or buy, each with the amount said for it, for the code's own step when the
     * model prepared no card for them (ChatServiceImpl):
     * - "Can you add Banana Robusta 1 dozen to my cart?" -> (Banana Robusta, 1, dozen);
     * - "add milk and 1 kg tomatoes" -> (milk, no amount: the add tool asks how much) and (tomatoes, 1, kg);
     * - "can you add one more item for Aashirvaad Whole Wheat Atta" -> (Aashirvaad Whole Wheat Atta, 1, no unit: a pack);
     * - "I want to buy this "Harpic Toilet Cleaner" product. Can you order it for me?" -> (Harpic Toilet Cleaner).
     * Only the sentences that ask to add or buy count, split into one part per product (at commas and "and"), each
     * without the words of the request. Like listedProducts, only what is exact counts: a part with one number and its
     * unit, or with no amount at all. The list is empty when a part has an unclear amount ("2k", "two kilos", "6 eggs",
     * a bare "2"): the model asks the customer about those, the code never guesses.
     */
    public static List<ListedProduct> productsToAdd(String message) {
        if (message == null) {
            return List.of();
        }
        List<ListedProduct> products = new ArrayList<>();
        for (String sentence : SENTENCE_END.split(message)) {
            if (!addAsked(sentence) && !buyAsked(sentence)) {
                continue;
            }
            for (String part : LIST_BREAK.split(sentence)) {
                Matcher amount = AMOUNT_WITH_UNIT.matcher(part);
                boolean hasAmount = amount.find();
                String rest = hasAmount ? part.substring(0, amount.start()) + " " + part.substring(amount.end()) : part;
                if (namesAnAmount(rest)) {
                    return List.of();       // an amount that is not one number with its unit
                }
                String product = Arrays.stream(rest.split("[^\\p{L}\\p{M}\\p{N}]+"))
                        .filter(word -> !word.isEmpty() && !word.matches("\\d+") && !isNoName(word))
                        .collect(Collectors.joining(" "));
                if (product.isEmpty()) {
                    if (hasAmount) {
                        return List.of();   // an amount without a product: "add 2 kg of it"
                    }
                    continue;               // only words of the request: "Can you order it for me?"
                }
                products.add(hasAmount
                        ? new ListedProduct(product, new BigDecimal(amount.group(1).replace(',', '.')), toolUnit(amount.group(2)))
                        : ONE_SAID.matcher(part).find()
                        ? new ListedProduct(product, BigDecimal.ONE, null)      // "one more item for X", "add a tomato"
                        : new ListedProduct(product, null, null));             // no amount: the add tool asks how much
            }
        }
        return products;
    }

    /**
     * A word of the request ("can", "add", "cart"), an add or buy word, a unit, or a way to pay ("by online payment"): no
     * part of a product's name
     */
    private static boolean isNoName(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        return REQUEST_WORDS.contains(lower) || NOT_A_NAME.contains(lower) || PAYMENT_WORDS.contains(lower)
                || ADD_WORDS.matcher(word).matches() || BUY_WORDS.matcher(word).matches();
    }

    /** "Akshayakalpa Organic Milk 1L" -> "Akshayakalpa Organic Milk": a product name without an amount in it */
    public static String withoutAmount(String product) {
        return product == null ? null : AMOUNT_WITH_UNIT.matcher(product).replaceAll(" ").replaceAll("\\s+", " ").strip();
    }

    /** "kilos" -> "kg", "litre" -> "l", "packets" -> "packs", "pcs" -> "pieces": the units prepareMyCartAdd knows */
    private static String toolUnit(String said) {
        String unit = said.toLowerCase(Locale.ROOT);
        if (unit.startsWith("k")) {
            return "kg";
        }
        if (unit.equals("ml")) {
            return "ml";
        }
        if (unit.startsWith("l")) {
            return "l";
        }
        if (unit.startsWith("g")) {
            return "g";
        }
        if (unit.startsWith("d")) {
            return "dozen";
        }
        return unit.startsWith("pack") || unit.startsWith("bottle") ? "packs" : "pieces";
    }

    /**
     * The part of the customer's words about one product, for the amount check. "Add Tomato 1 kg, Onion 2 kg and Potato
     * 3 kg" says an amount for each product: an amount for the tomatoes must be in "Add Tomato 1 kg", not taken from
     * "Potato 3 kg". The message is split at commas, "and" and "&"; its parts that name the product count when they say
     * an amount. Otherwise all the words count: "add tomatoes" then "2 kg" (the answer to "how much?"), "make it 3 kg"
     * after "add 2 kg tomatoes", or a name with "and" in it.
     *
     * @param message       the customer's message of this question
     * @param customerWords that message and the one before it
     * @param product       the tool call's "product"
     */
    public static String wordsAbout(String message, String customerWords, Object product) {
        Set<String> productWords = productWords(product);
        if (message == null || productWords.isEmpty()) {
            return customerWords;
        }
        String parts = LIST_BREAK.splitAsStream(message)
                .filter(part -> productWords(part).containsAll(productWords))
                .collect(Collectors.joining("\n"));
        return namesAnAmount(parts) ? parts : customerWords;
    }

    /**
     * The amount the customer said for this product in this message: the one number with its unit in the parts that
     * name it ("can you add Akshayakalpa Organic Milk 1L and Organic Carrot 2 kg" -> 2 kg for the carrot). Null when no
     * part names the product with such an amount, or they say two different amounts.
     *
     * @param message the customer's message of this question only (not the one before: "make it 3 kg" after "add 2 kg
     *                tomatoes" is 3 kg)
     */
    public static ListedProduct amountSaidFor(String message, Object product) {
        Set<String> productWords = productWords(product);
        if (message == null || productWords.isEmpty()) {
            return null;
        }
        List<ListedProduct> said = new ArrayList<>();
        for (String part : LIST_BREAK.split(withAsciiDigits(message))) {
            if (!productWords(part).containsAll(productWords)) {
                continue;
            }
            Matcher amount = AMOUNT_WITH_UNIT.matcher(part);
            while (amount.find()) {
                ListedProduct one = new ListedProduct(product.toString(), new BigDecimal(amount.group(1).replace(',', '.')),
                        toolUnit(amount.group(2)));
                if (said.stream().noneMatch(other -> sameAmount(other, one.quantity(), one.unit()))) {
                    said.add(one);
                }
            }
        }
        return said.size() == 1 ? said.getFirst() : null;
    }

    /**
     * Whether a tool call's quantity and unit are this amount: 2 with "kg" or "kilos" is 2 kg; no unit means packs, and
     * no quantity one pack (as prepareMyCartAdd reads them)
     */
    public static boolean sameAmount(ListedProduct said, Object quantity, Object unit) {
        BigDecimal number = numberOf(quantity);
        String callUnit = unit == null || unit.toString().isBlank() ? "packs" : unit.toString().strip().toLowerCase(Locale.ROOT);
        if (AMOUNT_WITH_UNIT.matcher("1 " + callUnit).matches()) {
            callUnit = toolUnit(callUnit);
        }
        return (number == null ? BigDecimal.ONE : number).compareTo(said.quantity()) == 0 && callUnit.equals(said.unit());
    }

    /** The words of a product's name, lower case and singular, without "the", numbers or units: "2 kg Tomatoes" -> [tomato] */
    private static Set<String> productWords(Object text) {
        if (text == null) {
            return Set.of();
        }
        Set<String> words = new HashSet<>();
        // letters and their marks (Devanagari vowel signs), no digits
        for (String word : text.toString().toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{M}]+")) {
            if (!word.isEmpty() && !NOT_A_NAME.contains(word)) {
                words.add(singular(word));
            }
        }
        return words;
    }

    /** "tomatoes" -> "tomato", "onions" -> "onion": the customer and the model may write either */
    private static String singular(String word) {
        if (word.length() > 4 && word.endsWith("oes")) {
            return word.substring(0, word.length() - 2);
        }
        if (word.length() > 3 && word.endsWith("s") && !word.endsWith("ss")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }

    private static boolean asks(String message) {
        return message != null && CHANGE_WORDS.matcher(message).find();
    }

    /**
     * A number as digits or as a number word. 1 also as "a", "an", "another" ("add a tomato", "one more"), but it must be
     * said too: "add tomato" says no amount, and the add tool then asks how much instead of choosing one pack.
     */
    private static boolean isSaid(BigDecimal number, String words) {
        String digits = number.toPlainString();
        // "2" is in "add 2k tomatoes", but not in "12 eggs" or "2.5 kg"
        if (Pattern.compile("(?<![\\d.,])" + Pattern.quote(digits) + "(?!\\d|[.,]\\d)").matcher(words).find()) {
            return true;
        }
        if (number.compareTo(BigDecimal.ONE) == 0) {
            return ONE_SAID.matcher(words).find();
        }
        List<String> numberWords = NUMBER_WORDS.get(digits);
        return numberWords != null && anyOf(numberWords).matcher(withoutEnglishDo(words)).find();
    }

    /** The words without the English verb "do" ("I do not want ..."), which is no Hindi "do" (two): see ENGLISH_DO */
    private static String withoutEnglishDo(String text) {
        return ENGLISH_DO.matcher(text).replaceAll(" ");
    }

    /** A weight or volume unit must be said: "kg" is in "2 kg", "2kg" and "two kilos", not in "2k". Counts need no unit. */
    private static boolean isSaid(String unitWord, String words) {
        return UNITS.stream()
                .filter(spellings -> spellings.contains(unitWord))
                .findFirst()
                .map(spellings -> Pattern.compile("(?iU)(?:\\b|(?<=\\d))(?:" + String.join("|", spellings) + ")\\b").matcher(words).find())
                .orElse(true);      // packs, pieces, dozen, "eggs": counts (mcp-server-service asks when they do not fit)
    }

    /** 2, 2.0 and "2" are the same number; null for no number */
    private static BigDecimal numberOf(Object quantity) {
        if (quantity == null) {
            return null;
        }
        try {
            return new BigDecimal(quantity.toString().strip()).stripTrailingZeros();
        } catch (NumberFormatException ex) {
            return null;        // not a number: mcp-server-service answers INVALID_INPUT
        }
    }

    /** "२ किलो" -> "2 किलो" */
    private static String withAsciiDigits(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder ascii = new StringBuilder(text.length());
        for (char character : text.toCharArray()) {
            int devanagari = DEVANAGARI_DIGITS.indexOf(character);
            ascii.append(devanagari >= 0 ? (char) ('0' + devanagari) : character);
        }
        return ascii.toString();
    }

    private static Pattern anyOf(List<String> words) {
        return Pattern.compile("(?iU)\\b(?:" + words.stream()
                .map(word -> Pattern.quote(word).replace(" ", "\\E\\s+\\Q"))
                .collect(Collectors.joining("|")) + ")\\b");
    }
}
