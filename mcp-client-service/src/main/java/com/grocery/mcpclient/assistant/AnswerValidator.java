package com.grocery.mcpclient.assistant;

import com.grocery.mcpclient.tool.ToolCallRecorder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Checks the model's answer in code, instead of trusting it (hallucination prevention, layer 4).
 *
 * An answer passes only when:
 * 1. it is not empty and has no leftovers of the model's internals (thinking tags, tool-call syntax, raw JSON,
 *    sentences of its own rules);
 * 2. it makes no claim about the range of products ("we don't sell phones") or about stock ("in stock", "3 left")
 *    unless a tool was called for this question: such facts change, so an earlier answer is not good enough;
 * 3. every rupee amount ("₹56", "Rs. 56", "56 rupees") appears in this question's tool results (or was a tool
 *    argument, like the customer's price limit);
 * 4. every percentage ("10%") appears in the tool results;
 * 5. every coupon-like code (capital letters with digits, such as SAVE10) appears in the tool results;
 * 6. it never says that the cart was changed ("I added 2 kg of tomatoes to your cart", "I've removed it from your
 *    cart"): no tool changes the cart, only the customer's click on a card does (it may say what will happen after the
 *    click: "It will be added to your cart once you press Confirm");
 * 7. it never says that an order is placed or cancelled now, or that something is paid ("Your order has been placed",
 *    "I cancelled your order", "your payment is received"): the chat places, cancels and takes payment for nothing, only
 *    the customer's click on Confirm under a summary places or cancels. (It may report an order's status that an order
 *    tool read in this turn, "Your order 36 has been confirmed", but never next to a summary.)
 * 8. it says that a summary is ready ("Your order has been prepared for cancellation") or tells the customer to press
 *    Confirm only when a prepare tool made a card in this turn (an order, a cancellation, or a change of the cart):
 *    the summary the customer confirms is the card under the answer, never the model's text (after "yes" under an
 *    earlier card, ChatService's fixed answers say it);
 * 9. every order number ("order number 36", "#36") appears in this question's tool results or arguments;
 * 10. it never offers to place, cancel or pay for an order ("I can place the order with cash on delivery"), and it never
 *    only announces a tool call ("Let me search for it.") instead of making it.
 *
 * So a price, discount, code or order number can never reach the customer unless a tool returned it for this question.
 * Other facts ("it has 4.5 stars") cannot be matched word by word; the system prompt and the tool design
 * (short, ready-made texts) cover those.
 */
@Component
public class AnswerValidator {

    private static final Pattern RUPEES_BEFORE = Pattern.compile(
            "(?:₹|\\bRs\\.?|\\bINR)\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RUPEES_AFTER = Pattern.compile(
            "\\b([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(?:rupees?|rs\\b)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PERCENTAGE = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*%");
    private static final Pattern PLAIN_NUMBER = Pattern.compile("\\b([0-9]+(?:\\.[0-9]+)?)\\b");
    /** 4-20 capital letters and digits with at least one of each, like SAVE10 or GREENWEEK15. */
    private static final Pattern CODE = Pattern.compile("\\b(?=[A-Z0-9]*[A-Z])(?=[A-Z0-9]*[0-9])[A-Z0-9]{4,20}\\b");
    private static final List<String> INTERNAL_MARKERS =
            List.of("<think>", "</think>", "<tool_call>", "</tool_call>", "\"status\":", "\"arguments\":");
    /** "We don't sell ...", "could not find ...": claims about the range of products, which need a search first. */
    private static final Pattern NOT_SOLD_CLAIM = Pattern.compile(
            "\\b(?:do|does|did)\\s*(?:not|n't|n’t)\\s+(?:sell|stock|carry)\\b|\\bnot\\s+sold\\b|\\bonly\\s+sells?\\b"
                    + "|\\bspeciali[sz]e[sd]?\\s+in\\b|\\bcould\\s*(?:not|n't|n’t)\\s+find\\b",
            Pattern.CASE_INSENSITIVE);
    /** "in stock", "out of stock", "sold out", "3 left", "5 pieces available". */
    private static final Pattern STOCK_CLAIM = Pattern.compile(
            "\\b(?:in|out\\s+of)\\s+stock\\b|\\bsold\\s+out\\b|\\b\\d+\\s+(?:\\w+\\s+)?(?:left|available)\\b",
            Pattern.CASE_INSENSITIVE);
    /** A change of the cart, in a sentence that also says "cart": "Added 2 x Tomato to your cart." */
    private static final Pattern CHANGED = Pattern.compile(
            "\\b(?:added|removed|deleted|updated|changed|increased|decreased|reduced|put|placed|taken\\s+out|took\\s+out"
                    + "|emptied|cleared)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CART = Pattern.compile("\\b(?:cart|basket)\\b", Pattern.CASE_INSENSITIVE);
    /** "could not be added", "nothing was changed": no claim that something changed */
    private static final Pattern NEGATION = Pattern.compile(
            "\\b(?:not|never|no|nothing|cannot|unable|failed)\\b|n['’]t\\b", Pattern.CASE_INSENSITIVE);
    /**
     * "Your order has been (successfully) placed", "I placed your order", "Order confirmed!", "placed successfully" (but not
     * the history: "was placed on 26 Oct")
     */
    private static final Pattern ORDER_PLACED = Pattern.compile(
            "\\b(?:has|have|had)\\s+been\\s+(?:successfully\\s+)?(?:placed|confirmed)\\b"
                    + "|\\b(?:is|got)\\s+(?:now\\s+)?(?:successfully\\s+)?(?:placed|confirmed)\\b"
                    + "|\\b(?:I|we)(?:'ve|\\s+have)?\\s+(?:successfully\\s+)?(?:placed|confirmed)\\b"
                    + "|\\border\\s+(?:placed|confirmed)\\b|\\b(?:placed|confirmed)\\s+successfully\\b",
            Pattern.CASE_INSENSITIVE);
    /**
     * "Your order has been (successfully) cancelled", "I cancelled your order", "Order cancelled!", "cancelled
     * successfully" (but not "was cancelled earlier": the past)
     */
    private static final Pattern ORDER_CANCELLED = Pattern.compile(
            "\\b(?:has|have|had)\\s+been\\s+(?:successfully\\s+)?cancell?ed\\b"
                    + "|\\b(?:is|got)\\s+(?:now\\s+)?(?:successfully\\s+)?cancell?ed\\b"
                    + "|\\b(?:I|we)(?:'ve|\\s+have)?\\s+(?:successfully\\s+)?cancell?ed\\b"
                    + "|\\border\\s+cancell?ed\\b|\\bcancell?ed\\s+successfully\\b",
            Pattern.CASE_INSENSITIVE);
    /** "Your payment is received", "the order has been (successfully) paid", "you have paid", "I charged ..." */
    private static final Pattern PAID = Pattern.compile(
            "\\bpayment\\s+(?:has\\s+been|is|was)\\s+(?:successfully\\s+)?(?:received|successful|completed|done|confirmed)\\b"
                    + "|\\b(?:has|have)\\s+been\\s+(?:successfully\\s+)?paid\\b|\\bis\\s+(?:now\\s+)?paid\\b"
                    + "|\\byou(?:'ve|\\s+have)\\s+paid\\b|\\b(?:I|we)(?:'ve|\\s+have)?\\s+(?:charged|collected)\\b"
                    + "|\\bpaid\\s+successfully\\b",
            Pattern.CASE_INSENSITIVE);
    /**
     * "Your order has been prepared for cancellation", "I have prepared ...", "Here is the summary", "The summary is
     * ready", "Press Confirm to cancel it", "Confirm the order under the summary": each says that a summary with a
     * Confirm button is under this answer
     */
    private static final Pattern SUMMARY_READY = Pattern.compile(
            "\\b(?:has|have)\\s+been\\s+prepared\\b|\\b(?:I|we)(?:'ve|\\s+have)?\\s+prepared\\b"
                    + "|\\bhere(?:\\s+is|'s)\\s+(?:the|your)\\s+(?:\\w+\\s+){0,2}summary\\b|\\bsummary\\s+is\\s+(?:now\\s+)?ready\\b"
                    + "|\\b(?:press|click|tap)(?:ing)?\\s+(?:the\\s+)?[\"“']?confirm\\b"
                    + "|\\bunder\\s+(?:the|this|your)\\s+(?:order\\s+|cancellation\\s+)?summary\\b",
            Pattern.CASE_INSENSITIVE);
    /**
     * "Let me search for it.", "I'll check that.", "I'll add it to your cart", "I'll remove it": an answer that only
     * announces a tool call.
     * The model said "... Let me search for it." and, once, "To order it, I'll add it to your cart and prepare the order
     * summary." to "I want to buy Harpic Toilet Cleaner, can you order it for me?" and called no such tool. (Not "a
     * moment" or "please wait": "You placed this order a moment ago", "please wait for the delivery" are real answers.)
     */
    private static final Pattern PROMISE = Pattern.compile(
            "\\blet\\s+me\\s+(?:\\w+\\s+)?(?:search|check|look|find|see|get|fetch|add|place|prepare|verify|remove|change|empty|clear)\\b"
                    + "|\\bI(?:'ll|\\s+will)\\s+(?:now\\s+|quickly\\s+|first\\s+)?"
                    + "(?:search|check|look\\s+(?:it\\s+)?up|find|fetch|verify|add|put|prepare|place|remove|change|empty|clear)\\b",
            Pattern.CASE_INSENSITIVE);
    /**
     * "I can place the order with cash on delivery.", "I can order the Harpic Toilet Cleaner for you.", "I'll go ahead
     * and cancel it.": the chat can do none of these
     */
    private static final Pattern OFFER_TO_ACT = Pattern.compile(
            "\\bI(?:\\s+can|\\s+could|'ll|\\s+will|\\s+am\\s+able\\s+to)\\s+(?:now\\s+)?(?:go\\s+ahead\\s+and\\s+)?"
                    + "(?:place|order|cancel|pay\\s+for)\\b",
            Pattern.CASE_INSENSITIVE);
    /** The problem of an answer that only announces what the model is going to do */
    public static final String PROMISE_PROBLEM = "the answer only says what you are going to do (\"let me search ...\") instead of "
            + "doing it: call the tool now and answer with its result";
    /** The problem of an answer that offers to place, cancel or pay; ChatService's last resort is NO_SUMMARY_ANSWER */
    public static final String OFFER_PROBLEM = "the answer offers to place, cancel or pay for an order yourself, which you "
            + "cannot do: to buy a product, call prepareMyCartAdd; to order the cart, prepareMyOrder; to cancel, "
            + "prepareMyOrderCancellation; the customer confirms the summary with its Confirm button";
    /** "order number 36", "order number is 36", "order no. 36", "#36" */
    private static final Pattern ORDER_NUMBER = Pattern.compile(
            "(?:\\border\\s+(?:number|no\\.?)\\s*(?:is\\s+|:\\s*)?#?\\s*|#\\s?)([0-9]+)\\b", Pattern.CASE_INSENSITIVE);
    /**
     * The problem of an answer that shows a summary no tool made, or sends the customer to a Confirm button that is not
     * there. The retry note sends the model to the prepare tools; ChatService's last resort is NO_SUMMARY_ANSWER.
     */
    public static final String SUMMARY_PROBLEM = "the answer says that a summary is ready or tells the customer to press "
            + "Confirm, but no summary was prepared for this message: call prepareMyOrder to order, "
            + "prepareMyOrderCancellation to cancel (without a number when the customer did not say which order), and for "
            + "the cart prepareMyCartAdd, prepareMyCartQuantity, prepareMyCartRemoval or prepareMyCartClear (adding a "
            + "product, a new amount, taking a product out, emptying it); the website shows that summary with its Confirm "
            + "button";
    /**
     * The problem of an answer that says the cart changed: the chat never changes it. ("Can you remove Harpic Toilet
     * Cleaner from my cart?" got "I've removed it ..." without a tool; the retry note then only named getMyCart, so the
     * second attempt read the cart and said it again.) The retry note names the tools; ChatService's last resort is
     * NO_CART_CHANGE_ANSWER.
     */
    public static final String CART_CHANGE_PROBLEM = "the answer says that the cart was changed, but the chat never changes "
            + "it: to add a product, call prepareMyCartAdd; to change how much of it, take it out or empty the cart, "
            + "prepareMyCartQuantity, prepareMyCartRemoval or prepareMyCartClear (they only prepare a summary: the customer "
            + "makes the change with its Confirm button)";
    /** The problem of an answer that says an order is placed: ChatService then answers with PRESS_CONFIRM */
    public static final String PLACED_ORDER_PROBLEM = "the answer says that an order is placed, but the chat never places "
            + "orders: only the customer's click on Confirm under the order summary does";
    /** The problem of an answer that says an order is cancelled: ChatService then answers with PRESS_CONFIRM_CANCEL */
    public static final String CANCELLED_ORDER_PROBLEM = "the answer says that an order is cancelled, but the chat never "
            + "cancels orders: only the customer's click on Confirm under the cancellation summary does";
    static final String PAID_PROBLEM = "the answer says that something is paid, but the chat never takes or checks payments";
    /** The tools that read the customer's existing orders: their status may be "confirmed", "cancelled" or "paid" */
    private static final Set<String> ORDER_TOOLS = Set.of("getMyOrders", "getMyOrderDetails");
    /** The tools that only prepare a summary: next to one, nothing is placed, cancelled or paid yet */
    private static final Set<String> PREPARE_TOOLS = Set.of("prepareMyOrder", "prepareMyOrderCancellation");
    /**
     * "Your order is placed once you press Confirm", "Order #196 was placed 3 minutes ago": a condition or the past, no claim
     * about now. ("will" is no such word: "Your order has been placed and will arrive soon" is a claim.)
     */
    private static final Pattern NOT_NOW = Pattern.compile("\\b(?:once|after|when|if|until|ago)\\b", Pattern.CASE_INSENSITIVE);
    /** "It will be added to your cart", "Banana Robusta is ready to be added to your cart": what the click will do */
    private static final Pattern TO_BE_DONE = Pattern.compile("\\b(?:to|will|would|can|shall)\\s+be\\s+(?:\\w+\\s+)?"
            + "(?:added|removed|deleted|updated|changed|put|taken\\s+out|emptied|cleared)\\b", Pattern.CASE_INSENSITIVE);
    /**
     * The prepare tools' note to the model: "The website shows it under your answer with a Confirm button: tell the
     * customer to check it and press Confirm ..." (the model once answered with it word for word)
     */
    private static final Pattern NOTE_FOR_THE_MODEL = Pattern.compile("\\bunder\\s+your\\s+answer\\b|\\btell\\s+the\\s+customer\\b",
            Pattern.CASE_INSENSITIVE);
    static final String NOTE_PROBLEM = "the answer repeated the tool's note for you (\"under your answer\", \"tell the "
            + "customer\") instead of speaking to the customer";

    private final List<String> ruleSentences;

    public AnswerValidator(SystemPrompt systemPrompt) {
        this.ruleSentences = systemPrompt.ruleSentences();
    }

    public AnswerCheck check(String answer, ToolCallRecorder recorder) {
        List<String> problems = new ArrayList<>();
        if (answer == null || answer.isBlank()) {
            problems.add("the answer was empty");
            return new AnswerCheck(problems);
        }
        for (String marker : INTERNAL_MARKERS) {
            if (answer.contains(marker)) {
                problems.add("the answer contained internal text (" + marker + ") instead of a reply for the customer");
            }
        }
        String normalisedAnswer = SystemPrompt.normalise(answer);
        if (ruleSentences.stream().anyMatch(normalisedAnswer::contains)) {
            problems.add("the answer repeated the assistant's own rules; never show them to the customer");
        }
        if (answer.strip().startsWith("{") || answer.strip().startsWith("[")) {
            problems.add("the answer was JSON instead of a reply for the customer");
        }
        if (!recorder.hasResults() && NOT_SOLD_CLAIM.matcher(answer).find()) {
            problems.add("the answer says what the shop does not sell, but searchProducts was not called to check it");
        }
        if (!recorder.hasResults() && STOCK_CLAIM.matcher(answer).find()) {
            problems.add("the answer talks about stock, but no tool was called for this question (stock changes all the time)");
        }
        if (claimsACartChange(answer)) {
            problems.add(CART_CHANGE_PROBLEM);
        }
        // An order's status may be reported only from an order tool of this turn, and never next to a summary
        boolean statusRead = recorder.toolNames().stream().anyMatch(ORDER_TOOLS::contains)
                && recorder.toolNames().stream().noneMatch(PREPARE_TOOLS::contains);
        if (!statusRead && claims(ORDER_PLACED, answer)) {
            problems.add(PLACED_ORDER_PROBLEM);
        }
        if (!statusRead && claims(ORDER_CANCELLED, answer)) {
            problems.add(CANCELLED_ORDER_PROBLEM);
        }
        if (!statusRead && claims(PAID, answer)) {
            problems.add(PAID_PROBLEM);
        }
        // A summary to confirm exists only as the card that a prepare tool's result makes in this turn. (The model once
        // copied its earlier answer: "Your last order has been prepared for cancellation. The order number is 36.")
        if (ChatActions.of(recorder) == null && claims(SUMMARY_READY, answer)) {
            problems.add(SUMMARY_PROBLEM);
        }
        // Only next to a card: a shop's assistant may well say "tell the customer that the order is late"
        if (ChatActions.of(recorder) != null && NOTE_FOR_THE_MODEL.matcher(answer).find()) {
            problems.add(NOTE_PROBLEM);
        }
        // An offer or a plan that asks the customer ("... Would you like me to add it to your cart?") waits for their
        // answer, and the next message does it; only one that asks nothing leaves the customer waiting for nothing
        boolean asksTheCustomer = sentences(answer).anyMatch(sentence -> sentence.endsWith("?"));
        if (!asksTheCustomer && claims(OFFER_TO_ACT, answer)) {
            problems.add(OFFER_PROBLEM);
        }
        if (!asksTheCustomer && claims(PROMISE, answer)) {
            problems.add(PROMISE_PROBLEM);
        }

        String toolResults = recorder.allResults();
        Set<String> knownAmounts = amounts(toolResults);
        // A number the model searched with ("fruits under ₹100" -> maxPrice 100) may be repeated in the answer
        knownAmounts.addAll(numbers(PLAIN_NUMBER, recorder.allArguments()));
        Set<String> knownPercentages = numbers(PERCENTAGE, toolResults);

        for (String amount : amounts(answer)) {
            if (!knownAmounts.contains(amount)) {
                problems.add("the amount ₹" + amount + " was not returned by any tool");
            }
        }
        for (String percentage : numbers(PERCENTAGE, answer)) {
            if (!knownPercentages.contains(percentage)) {
                problems.add("the percentage " + percentage + "% was not returned by any tool");
            }
        }
        Matcher codes = CODE.matcher(answer);
        while (codes.find()) {
            if (!toolResults.contains(codes.group())) {
                problems.add("the code " + codes.group() + " was not returned by any tool");
            }
        }
        // An order number the customer asked about is a tool argument ("cancel order 199" -> orderNumber 199)
        Set<String> knownNumbers = numbers(PLAIN_NUMBER, toolResults + "\n" + recorder.allArguments());
        for (String number : numbers(ORDER_NUMBER, answer)) {
            if (!knownNumbers.contains(number)) {
                // The retry note names the tool: the model answered "cancel my last order" from the line "Order #210
                // placed with the Confirm button." without a tool, and then said it could not find the order
                problems.add("the order number " + number + " was not returned by any tool in this turn: read the order "
                        + "with a tool first (to cancel it, call prepareMyOrderCancellation with orderNumber " + number + ")");
            }
        }
        return new AnswerCheck(List.copyOf(new LinkedHashSet<>(problems)));
    }

    /**
     * A sentence that says the cart changed: not a question, not negated, and not about what the click will do ("It will
     * be added to your cart once you press Confirm")
     */
    static boolean claimsACartChange(String answer) {
        return sentences(answer)
                .filter(sentence -> !sentence.endsWith("?"))
                .anyMatch(sentence -> CHANGED.matcher(sentence).find() && CART.matcher(sentence).find()
                        && !NEGATION.matcher(sentence).find() && !NOT_NOW.matcher(sentence).find()
                        && !TO_BE_DONE.matcher(sentence).find());
    }

    /** A sentence (not a question, not negated, not about a condition or the past) that says what the pattern finds */
    private static boolean claims(Pattern claim, String answer) {
        return sentences(answer)
                .filter(sentence -> !sentence.endsWith("?"))
                .anyMatch(sentence -> claim.matcher(sentence).find() && !NEGATION.matcher(sentence).find()
                        && !NOT_NOW.matcher(sentence).find());
    }

    /** The answer's sentences (split after . ! ? and at line breaks), without the spaces around them */
    private static Stream<String> sentences(String answer) {
        return Arrays.stream(answer.split("(?<=[.!?])\\s+|\\n")).map(String::strip);
    }

    private static Set<String> amounts(String text) {
        Set<String> amounts = numbers(RUPEES_BEFORE, text);
        amounts.addAll(numbers(RUPEES_AFTER, text));
        return amounts;
    }

    /** The numbers of group 1, normalised so "1,250", "1250" and "1250.00" are the same. */
    private static Set<String> numbers(Pattern pattern, String text) {
        Set<String> numbers = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            numbers.add(normalise(matcher.group(1)));
        }
        return numbers;
    }

    static String normalise(String number) {
        return new BigDecimal(number.replace(",", "")).stripTrailingZeros().toPlainString();
    }
}
