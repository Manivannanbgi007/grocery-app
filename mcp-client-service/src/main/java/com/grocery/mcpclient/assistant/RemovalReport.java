package com.grocery.mcpclient.assistant;

import com.grocery.mcpclient.dto.response.ChatAction;
import com.grocery.mcpclient.tool.ChangeRequest;
import com.grocery.mcpclient.tool.ToolCallRecorder;
import com.grocery.mcpclient.tool.ToolCallRecorder.ToolCall;

import java.util.ArrayList;
import java.util.List;

/**
 * The answer when the customer asked to take SEVERAL products out of the cart in one message ("Remove Tomato and Potato
 * from my cart, but keep Onion"), written here from the prepareMyCartRemoval results, never from the model's words:
 *
 * <pre>
 * Here are the products you want to take out of your cart: Tomato (1 kg) and Potato (1 kg). Please check them and press Confirm to remove them; nothing is removed before that.
 * The cart has no product called "mango".
 * </pre>
 *
 * Why: the model prepares one product per call. With several products its answer cannot be trusted to say which of them
 * are on the card ("press Confirm to remove Tomato and Mango" when Mango is not in the cart). So the products on the card
 * are listed first, then the shop's own sentence for each product that could not be prepared.
 *
 * Nothing is removed by this answer: the card's Confirm button takes the products out. With one product on a card the
 * model prepared, ChatServiceImpl uses it too (never the model's own words under a cart card).
 */
public final class RemovalReport {

    private RemovalReport() {
    }

    /** Whether the answer is this report: two or more prepareMyCartRemoval calls, and no card of another kind */
    public static boolean needed(ToolCallRecorder recorder) {
        ChatAction card = ChatActions.of(recorder);
        boolean removalCardOrNone = card == null || ChatAction.REMOVE_FROM_CART.equals(card.kind());
        return removalCardOrNone && removalCalls(recorder).size() >= 2;
    }

    /** The report: the products on the card, then one sentence for each product that is not on it */
    public static String of(ToolCallRecorder recorder) {
        ChatAction card = ChatActions.of(recorder);
        List<String> sentences = new ArrayList<>();
        if (card != null) {
            sentences.add(cardSentence(card.items().stream().map(RemovalReport::label).toList()));
        }
        for (ToolCall call : removalCalls(recorder)) {
            if (!"OK".equals(call.status()) && !isOnTheCard(call, card)) {
                String sentence = "CHOOSE".equals(call.status()) ? PurchaseSteps.whichOneToRemove(call) : firstSentence(call.message());
                if (sentence != null) {
                    sentences.add(sentence);
                }
            }
        }
        return String.join("\n", sentences);
    }

    private static List<ToolCall> removalCalls(ToolCallRecorder recorder) {
        return recorder.calls().stream().filter(call -> ChatActions.PREPARE_REMOVAL_TOOL.equals(call.toolName())).toList();
    }

    private static String cardSentence(List<String> labels) {
        if (labels.size() == 1) {
            return "Here is the product you want to take out of your cart: " + labels.getFirst()
                    + ". Please check it and press Confirm to remove it; nothing is removed before that.";
        }
        return "Here are the products you want to take out of your cart: "
                + String.join(", ", labels.subList(0, labels.size() - 1)) + " and " + labels.getLast()
                + ". Please check them and press Confirm to remove them; nothing is removed before that.";
    }

    /**
     * A call that failed for words that a product on the card covers: the model asked for "tomato and potato" (NOT_FOUND),
     * then Tomato and Potato were prepared one by one
     */
    private static boolean isOnTheCard(ToolCall call, ChatAction card) {
        return card != null && card.items().stream().anyMatch(line -> ChangeRequest.sameProduct(call.argument("product"), line.name()));
    }

    /** "Tomato (1 kg)" */
    private static String label(ChatAction.Line line) {
        return line.packSize() == null ? line.name() : line.name() + " (" + line.packSize() + ")";
    }

    /**
     * The shop's first sentence: "The cart has no product called "mango"." without the list of the cart's products that
     * follows it (the card and the cart page show the cart)
     */
    private static String firstSentence(String message) {
        return message == null ? null : message.split("(?<=\\.)\\s+", 2)[0];
    }
}
